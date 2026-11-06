package io.github.akarp0508.koifolio.transaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.FieldValidationException;
import io.github.akarp0508.koifolio.RequestChecker;
import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.audit.Actor;
import io.github.akarp0508.koifolio.audit.AuditLog;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.domain.Dividend;
import io.github.akarp0508.koifolio.domain.Trade;
import io.github.akarp0508.koifolio.instrument.InstrumentService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Every lookup is scoped to the user. Writes lock the account row, so concurrent transactions
 * can't lose a balance update.
 */
@Service
public class TransactionService {

    static final int MAX_IMPORT = 1000;

    private static final Comparator<Transaction> HISTORY_ORDER =
            Comparator.comparing(Transaction::getDate).thenComparing(Transaction::getCreatedAt);

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final AuthService authService;
    private final InstrumentService instruments;
    private final RequestChecker checker;
    private final AuditLog auditLog;

    TransactionService(TransactionRepository transactions, AccountRepository accounts, AuthService authService,
                       InstrumentService instruments, RequestChecker checker, AuditLog auditLog) {
        this.checker = checker;
        this.auditLog = auditLog;
        this.transactions = transactions;
        this.accounts = accounts;
        this.authService = authService;
        this.instruments = instruments;
    }

    record Listed(Transaction transaction, String currency, String ticker, boolean oversold) {
    }

    /** One row of an import. @param added empty if this row was refused, or none were added */
    record ImportRow(int row, Listed added, String error) {
    }

    /** @param importId empty if any row was refused, in which case none were added */
    record Imported(UUID importId, List<ImportRow> rows) {
    }

    /** A transaction for backup, with no ids of its own. */
    public record Exported(UUID accountId, TransactionType type, LocalDate date, String ticker, BigDecimal quantity,
                           BigDecimal price, BigDecimal fee, BigDecimal amount, BigDecimal withheldTax,
                           BigDecimal splitFrom, BigDecimal splitTo, String note, boolean affectsBalance, Instant createdAt,
                           String createdBy, boolean unvalidated) {
    }

    /** Changes to a transaction, with only the given fields set. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Edit(TransactionType type, LocalDate date, String ticker, BigDecimal quantity, BigDecimal price, BigDecimal fee,
                       BigDecimal amount, BigDecimal withheldTax, BigDecimal splitFrom, BigDecimal splitTo, String note, Boolean affectsBalance) {
    }

    /** Oldest first. */
    @Transactional(readOnly = true)
    public List<Exported> exportAll(UUID userId) {
        Map<UUID, String> tickers = instruments.views(userId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().ticker()));
        return transactions.findByUserId(userId).stream().sorted(HISTORY_ORDER)
                .map(t -> new Exported(t.getAccountId(), t.getType(), t.getDate(), tickers.get(t.getInstrumentId()),
                        t.getQuantity(), t.getPrice(), t.getFee(), t.getAmount(), t.getWithheldTax(), t.getSplitFrom(),
                        t.getSplitTo(), t.getNote(), t.isAffectsBalance(), t.getCreatedAt(), t.getCreatedBy(), t.isUnvalidated()))
                .toList();
    }

    /** Checks it as a new transaction would be, but leaves the balance alone, because the export has that as it stood. */
    @Transactional
    public void restore(UUID userId, Exported exported) {
        Account account = accounts.findByIdAndUserId(exported.accountId(), userId).orElseThrow();
        TransactionRequest request = new TransactionRequest(exported.accountId(), exported.type(), exported.date(),
                exported.ticker(), exported.quantity(), exported.price(), exported.fee(), exported.amount(),
                exported.withheldTax(), exported.splitFrom(), exported.splitTo(), exported.note(), exported.affectsBalance(), true);
        checker.check(request);
        TransactionValues values = validate(request, account.getCurrency());
        Transaction transaction = new Transaction(userId, account.getId());
        transaction.apply(values, instrumentFor(userId, values));
        transaction.setNote(request.note());
        transaction.setCreatedAt(exported.createdAt());
        transaction.addedBy(exported.createdBy(), exported.unvalidated());
        transactions.save(transaction);
    }

    @Transactional(readOnly = true)
    List<Listed> list(UUID userId, UUID accountId) {
        return list(userId, accountId, false);
    }

    @Transactional(readOnly = true)
    List<Listed> list(UUID userId, UUID accountId, boolean onlyUnvalidated) {
        authService.currentUser(userId);
        List<Transaction> all = transactions.findByUserId(userId);
        Set<UUID> oversold = oversold(all);
        Map<UUID, String> currencies = currencies(userId);
        Map<UUID, String> tickers = instruments.tickers(userId);
        return all.stream()
                .filter(transaction -> accountId == null || transaction.getAccountId().equals(accountId))
                .filter(transaction -> !onlyUnvalidated || transaction.isUnvalidated())
                .sorted(HISTORY_ORDER.reversed())
                .map(transaction -> new Listed(transaction, currencies.get(transaction.getAccountId()),
                        tickers.get(transaction.getInstrumentId()), oversold.contains(transaction.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    Listed get(UUID userId, UUID id) {
        authService.currentUser(userId);
        return listed(userId, find(userId, id));
    }

    @Transactional
    Listed create(UUID userId, TransactionRequest request, Actor actor) {
        return create(userId, request, actor, null);
    }

    /** Adds all the rows or none of them, and reports on every row either way. Rows run in order, so each sees the balance the last left. */
    @Transactional
    Imported createAll(UUID userId, List<TransactionRequest> requests, Actor actor) {
        if (requests.isEmpty() || requests.size() > MAX_IMPORT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_import", "Send between 1 and %d transactions".formatted(MAX_IMPORT));
        }
        UUID importId = UUID.randomUUID();
        List<ImportRow> rows = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            try {
                checker.check(requests.get(i));
                rows.add(new ImportRow(i + 1, create(userId, requests.get(i), actor, importId), null));
            } catch (ApiException | FieldValidationException e) {
                rows.add(new ImportRow(i + 1, null, e.getMessage()));
            }
        }
        if (rows.stream().allMatch(row -> row.error() == null)) {
            return new Imported(importId, rows);
        }
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return new Imported(null, rows.stream().map(row -> new ImportRow(row.row(), null, row.error())).toList());
    }

    private Listed create(UUID userId, TransactionRequest request, Actor actor, UUID importId) {
        authService.currentUser(userId);
        if (request.accountId() == null) {
            throw new FieldValidationException(Map.of("accountId", "must not be null"));
        }
        Account account = lockAccount(userId, request.accountId());
        TransactionValues values = validate(request, account.getCurrency());
        Transaction transaction = new Transaction(userId, account.getId());
        BigDecimal balanceBefore = account.getBalance();
        transaction.apply(values, instrumentFor(userId, values));
        transaction.setNote(request.note());
        account.adjustBalance(transaction.balanceEffect());
        requireAcceptableBalance(account, balanceBefore, request);
        if (actor.isAgent()) {
            transaction.addedBy(actor.name(), true);
        }
        transaction.setImportId(importId);
        Listed saved = listed(userId, transactions.save(transaction));
        log(userId, actor, transaction, "create", "Added", null, snapshot(saved));
        return saved;
    }

    @Transactional
    Listed update(UUID userId, UUID id, TransactionRequest request, Actor actor) {
        authService.currentUser(userId);
        Transaction transaction = find(userId, id);
        Map<String, Object> before = snapshot(listed(userId, transaction));
        Account account = lockAccount(userId, transaction.getAccountId());
        TransactionValues values = validate(request, account.getCurrency());
        BigDecimal balanceBefore = account.getBalance();
        account.adjustBalance(transaction.balanceEffect().negate());
        transaction.apply(values, instrumentFor(userId, values));
        transaction.setNote(request.note());
        account.adjustBalance(transaction.balanceEffect());
        requireAcceptableBalance(account, balanceBefore, request);
        Listed saved = listed(userId, transaction);
        log(userId, actor, transaction, "update", "Changed", before, snapshot(saved));
        return saved;
    }

    /** Refuses what {@link #applyEdit} would, so a proposal fails early instead of at approval. */
    @Transactional(readOnly = true)
    public void check(UUID userId, UUID id, Edit edit) {
        authService.currentUser(userId);
        Transaction transaction = find(userId, id);
        TransactionRequest request = merged(userId, transaction, edit);
        checker.check(request);
        validate(request, currencies(userId).get(transaction.getAccountId()));
    }

    /** Applies the fields over what the transaction holds by then. The user has approved it, so a negative balance is allowed. */
    @Transactional
    public void applyEdit(UUID userId, UUID id, Edit edit, Actor actor) {
        update(userId, id, merged(userId, find(userId, id), edit), actor);
    }

    /** Puts back the values from before an edit. A log entry leaves out what was empty, so an empty note stays empty. */
    @Transactional
    public void revertEdit(UUID userId, UUID id, Edit before) {
        applyEdit(userId, id, new Edit(before.type(), before.date(), before.ticker(), before.quantity(), before.price(), before.fee(),
                before.amount(), before.withheldTax(), before.splitFrom(), before.splitTo(), first(before.note(), ""),
                before.affectsBalance()), Actor.USER);
    }

    private TransactionRequest merged(UUID userId, Transaction t, Edit edit) {
        return new TransactionRequest(null, first(edit.type(), t.getType()), first(edit.date(), t.getDate()),
                first(edit.ticker(), instruments.tickers(userId).get(t.getInstrumentId())), first(edit.quantity(), t.getQuantity()),
                first(edit.price(), t.getPrice()), first(edit.fee(), t.getFee()), first(edit.amount(), t.getAmount()),
                first(edit.withheldTax(), t.getWithheldTax()), first(edit.splitFrom(), t.getSplitFrom()),
                first(edit.splitTo(), t.getSplitTo()), first(edit.note(), t.getNote()), first(edit.affectsBalance(), t.isAffectsBalance()), true);
    }

    private static <T> T first(T value, T fallback) {
        return value == null ? fallback : value;
    }

    /** The transaction's values, as they'd show in the change log. */
    @Transactional(readOnly = true)
    public Map<String, Object> values(UUID userId, UUID id) {
        authService.currentUser(userId);
        return snapshot(listed(userId, find(userId, id)));
    }

    /** Names a transaction in a summary, like "buy PAS on 2026-03-12". */
    @Transactional(readOnly = true)
    public String describe(UUID userId, UUID id) {
        authService.currentUser(userId);
        Transaction transaction = find(userId, id);
        return description(transaction, instruments.tickers(userId).get(transaction.getInstrumentId()));
    }

    private static String description(Transaction transaction, Object ticker) {
        return "%s%s on %s".formatted(transaction.getType().name().toLowerCase(Locale.ROOT), ticker == null ? "" : " " + ticker,
                transaction.getDate());
    }

    @Transactional
    public void delete(UUID userId, UUID id, Actor actor) {
        authService.currentUser(userId);
        remove(userId, find(userId, id), actor, "delete", "Deleted");
    }

    /** Brings a deleted transaction back, and its effect on the balance with it. */
    @Transactional
    public void undelete(UUID userId, UUID id) {
        authService.currentUser(userId);
        if (transactions.undelete(id, userId) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "transaction_not_deleted", "This transaction isn't deleted");
        }
        Transaction transaction = find(userId, id);
        Account account = lockAccount(userId, transaction.getAccountId());
        account.adjustBalance(transaction.balanceEffect());
        requireBalanceInRange(account);
        log(userId, Actor.USER, transaction, "update", "Restored", null, snapshot(listed(userId, transaction)));
    }

    /** Clears the mark. The transaction already counts, so nothing else changes. */
    @Transactional
    Listed approve(UUID userId, UUID id) {
        authService.currentUser(userId);
        return approve(userId, requireUnvalidated(find(userId, id)));
    }

    /**
     * @param agent limits it to one agent's, or null for everyone's
     * @param importId limits it to one import, or null for all of them
     * @return how many were approved
     */
    @Transactional
    public int approveAll(UUID userId, String agent, UUID importId) {
        authService.currentUser(userId);
        List<Transaction> waiting = waiting(userId, agent, importId);
        waiting.forEach(transaction -> approve(userId, transaction));
        return waiting.size();
    }

    /**
     * @param agent limits it to one agent's, or null for everyone's
     * @param importId limits it to one import, or null for all of them
     * @return how many were rejected
     */
    @Transactional
    public int rejectAll(UUID userId, String agent, UUID importId) {
        authService.currentUser(userId);
        List<Transaction> waiting = waiting(userId, agent, importId);
        waiting.forEach(transaction -> remove(userId, transaction, Actor.USER, "reject", "Rejected"));
        return waiting.size();
    }

    private List<Transaction> waiting(UUID userId, String agent, UUID importId) {
        return transactions.findByUserIdAndValidatedAtIsNull(userId).stream()
                .filter(transaction -> agent == null || agent.equals(transaction.getCreatedBy()))
                .filter(transaction -> importId == null || importId.equals(transaction.getImportId()))
                .toList();
    }

    /** Takes it out of every number and undoes its effect on the balance, but keeps it in the database. */
    @Transactional
    void reject(UUID userId, UUID id) {
        authService.currentUser(userId);
        remove(userId, requireUnvalidated(find(userId, id)), Actor.USER, "reject", "Rejected");
    }

    public int countUnvalidated(UUID userId) {
        return transactions.countByUserIdAndValidatedAtIsNull(userId);
    }

    private Listed approve(UUID userId, Transaction transaction) {
        Map<String, Object> before = snapshot(listed(userId, transaction));
        transaction.approve();
        Listed saved = listed(userId, transaction);
        log(userId, Actor.USER, transaction, "approve", "Approved", before, snapshot(saved));
        return saved;
    }

    private void remove(UUID userId, Transaction transaction, Actor actor, String action, String verb) {
        Map<String, Object> before = snapshot(listed(userId, transaction));
        Account account = lockAccount(userId, transaction.getAccountId());
        account.adjustBalance(transaction.balanceEffect().negate());
        requireBalanceInRange(account);
        transaction.softDelete();
        log(userId, actor, transaction, action, verb, before, null);
    }

    private static Transaction requireUnvalidated(Transaction transaction) {
        if (!transaction.isUnvalidated()) {
            throw new ApiException(HttpStatus.CONFLICT, "transaction_already_validated", "This transaction isn't waiting for review");
        }
        return transaction;
    }

    private void log(UUID userId, Actor actor, Transaction transaction, String action, String verb, Map<String, Object> before,
                     Map<String, Object> after) {
        Map<String, Object> values = after == null ? before : after;
        String summary = verb + " " + description(transaction, values.get("ticker"));
        auditLog.record(userId, actor, new AuditLog.Change("transaction", transaction.getId(), action, summary, before, after));
    }

    /** The transaction's values for the audit log, without the empty ones. */
    private static Map<String, Object> snapshot(Listed listed) {
        Transaction t = listed.transaction();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("accountId", t.getAccountId());
        values.put("type", t.getType());
        values.put("date", t.getDate());
        values.put("ticker", listed.ticker());
        values.put("quantity", t.getQuantity());
        values.put("price", t.getPrice());
        values.put("fee", t.getFee());
        values.put("amount", t.getAmount());
        values.put("withheldTax", t.getWithheldTax());
        values.put("splitFrom", t.getSplitFrom());
        values.put("splitTo", t.getSplitTo());
        values.put("note", t.getNote());
        values.put("cashEffect", t.getCashEffect());
        values.put("affectsBalance", t.isAffectsBalance());
        values.put("createdBy", t.getCreatedBy());
        values.put("unvalidated", t.isUnvalidated());
        values.values().removeIf(Objects::isNull);
        return values;
    }

    /** Adds a split, as confirmed by the user. */
    @Transactional
    public void addSplit(UUID userId, UUID accountId, String ticker, LocalDate date, BigDecimal from, BigDecimal to) {
        create(userId, new TransactionRequest(accountId, TransactionType.SPLIT, date, ticker, null, null, null, null, null,
                from, to, null, false, null), Actor.USER);
    }

    /** Every buy, sell and split, oldest first. */
    @Transactional(readOnly = true)
    public List<AccountTrade> tradesOldestFirst(UUID userId) {
        return transactions.findByUserId(userId).stream()
                .filter(transaction -> transaction.getInstrumentId() != null && transaction.getType() != TransactionType.DIVIDEND)
                .sorted(HISTORY_ORDER)
                .map(transaction -> new AccountTrade(transaction.getAccountId(), transaction.getInstrumentId(),
                        transaction.getDate(), trade(transaction)))
                .toList();
    }

    /** Cash entering or leaving an account, signed. */
    public record CashMove(UUID accountId, LocalDate date, BigDecimal amount) {
    }

    /**
     * Every transaction's effect on cash, even those that didn't change the balance. Those are history the
     * balance already includes, so they matter when working out what it was.
     */
    @Transactional(readOnly = true)
    public List<CashMove> cashMoves(UUID userId) {
        return transactions.findByUserId(userId).stream()
                .map(transaction -> new CashMove(transaction.getAccountId(), transaction.getDate(), transaction.getCashEffect()))
                .toList();
    }

    /** Every dividend, oldest first. */
    @Transactional(readOnly = true)
    public List<AccountDividend> dividendsOldestFirst(UUID userId) {
        return transactions.findByUserId(userId).stream()
                .filter(transaction -> transaction.getType() == TransactionType.DIVIDEND)
                .sorted(HISTORY_ORDER)
                .map(transaction -> new AccountDividend(transaction.getAccountId(), transaction.getInstrumentId(),
                        new Dividend(transaction.getDate(), transaction.getAmount(), transaction.getWithheldTax())))
                .toList();
    }

    private static Trade trade(Transaction transaction) {
        return switch (transaction.getType()) {
            case BUY -> new Trade(Trade.Side.BUY, transaction.getQuantity(), transaction.getPrice(), transaction.getFee());
            case SELL -> new Trade(Trade.Side.SELL, transaction.getQuantity(), transaction.getPrice(), transaction.getFee());
            case SPLIT -> Trade.split(transaction.getSplitFrom(), transaction.getSplitTo());
            default -> throw new IllegalArgumentException("Not a trade: " + transaction.getType());
        };
    }

    private Map<UUID, String> currencies(UUID userId) {
        Map<UUID, String> currencies = new HashMap<>();
        accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId)
                .forEach(account -> currencies.put(account.getId(), account.getCurrency()));
        return currencies;
    }

    private Listed listed(UUID userId, Transaction transaction) {
        transactions.flush();
        boolean oversold = oversold(transactions.findByUserId(userId)).contains(transaction.getId());
        return new Listed(transaction, currencies(userId).get(transaction.getAccountId()),
                instruments.tickers(userId).get(transaction.getInstrumentId()), oversold);
    }

    private Transaction find(UUID userId, UUID id) {
        return transactions.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "transaction_not_found", "Transaction not found"));
    }

    private Account lockAccount(UUID userId, UUID accountId) {
        return accounts.findForUpdate(accountId, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "account_not_found", "Account not found"));
    }

    private UUID instrumentFor(UUID userId, TransactionValues values) {
        return values.ticker() == null ? null : instruments.useTicker(userId, values.ticker());
    }

    /** Only a change that lowers the balance below zero asks, so an already negative account doesn't nag. */
    private static void requireAcceptableBalance(Account account, BigDecimal balanceBefore, TransactionRequest request) {
        requireBalanceInRange(account);
        BigDecimal balance = account.getBalance();
        boolean goesNegative = balance.signum() < 0 && balance.compareTo(balanceBefore) < 0;
        if (goesNegative && !Boolean.TRUE.equals(request.allowNegativeBalance())) {
            throw new ApiException(HttpStatus.CONFLICT, "negative_balance", "This would take %s to %s %s".formatted(
                    account.getName(), rounded(balance, account.getCurrency()).toPlainString(), account.getCurrency()));
        }
    }

    private static void requireBalanceInRange(Account account) {
        if (account.getBalance().abs().compareTo(Account.BALANCE_LIMIT) >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "balance_out_of_range",
                    "This would take the account balance out of the supported range");
        }
    }

    /** Sales of more than was held at that point, walking each account's history in order. */
    private static Set<UUID> oversold(List<Transaction> all) {
        record Holding(UUID account, UUID instrument) {
        }

        Map<Holding, BigDecimal> held = new HashMap<>();
        Set<UUID> oversold = new HashSet<>();
        all.stream().sorted(HISTORY_ORDER).forEach(transaction -> {
            Holding holding = new Holding(transaction.getAccountId(), transaction.getInstrumentId());
            if (transaction.getType() == TransactionType.BUY) {
                held.merge(holding, transaction.getQuantity(), BigDecimal::add);
            } else if (transaction.getType() == TransactionType.SPLIT) {
                held.computeIfPresent(holding, (key, shares) -> shares.multiply(transaction.getSplitTo())
                        .divide(transaction.getSplitFrom(), MathContext.DECIMAL128));
            } else if (transaction.getType() == TransactionType.SELL) {
                BigDecimal remaining = held.getOrDefault(holding, BigDecimal.ZERO).subtract(transaction.getQuantity());
                if (remaining.signum() < 0) {
                    oversold.add(transaction.getId());
                }
                held.put(holding, remaining.max(BigDecimal.ZERO));
            }
        });
        return oversold;
    }

    private static TransactionValues validate(TransactionRequest request, String currency) {
        Map<String, String> errors = new LinkedHashMap<>();
        TransactionValues values = switch (request.type()) {
            case BUY, SELL -> trade(request, currency, errors);
            case DIVIDEND -> dividend(request, currency, errors);
            case DEPOSIT, WITHDRAWAL, FEE -> cash(request, currency, errors);
            case SPLIT -> split(request, errors);
        };
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        if (values.cashEffect().abs().compareTo(Account.BALANCE_LIMIT) >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "amount_out_of_range", "This transaction is too large");
        }
        return values;
    }

    private static TransactionValues trade(TransactionRequest request, String currency, Map<String, String> errors) {
        String ticker = ticker(request, errors);
        BigDecimal quantity = requirePositive("quantity", request.quantity(), errors);
        BigDecimal price = request.price();
        if (price == null) {
            errors.put("price", "must not be null");
        } else if (price.signum() < 0) {
            errors.put("price", "must not be negative");
        }
        BigDecimal fee = optionalAmount("fee", request.fee(), currency, errors);
        if (!errors.isEmpty()) {
            return null;
        }
        BigDecimal value = quantity.multiply(price);
        BigDecimal cashEffect = request.type() == TransactionType.BUY ? value.add(fee).negate() : value.subtract(fee);
        return new TransactionValues(request.type(), request.date(), ticker, quantity, price, fee, null, null,
                rounded(cashEffect, currency), request.affectsBalance(), null, null);
    }

    private static TransactionValues dividend(TransactionRequest request, String currency, Map<String, String> errors) {
        String ticker = ticker(request, errors);
        BigDecimal amount = requireAmount(request, currency, errors);
        BigDecimal withheldTax = optionalAmount("withheldTax", request.withheldTax(), currency, errors);
        if (amount != null && withheldTax.compareTo(amount) > 0) {
            errors.put("withheldTax", "must not be more than the amount");
        }
        if (!errors.isEmpty()) {
            return null;
        }
        return new TransactionValues(request.type(), request.date(), ticker, null, null, null, amount, withheldTax,
                amount.subtract(withheldTax), request.affectsBalance(), null, null);
    }

    private static TransactionValues cash(TransactionRequest request, String currency, Map<String, String> errors) {
        BigDecimal amount = requireAmount(request, currency, errors);
        if (!errors.isEmpty()) {
            return null;
        }
        BigDecimal cashEffect = request.type() == TransactionType.DEPOSIT ? amount : amount.negate();
        return new TransactionValues(request.type(), request.date(), null, null, null, null, amount, null,
                cashEffect, request.affectsBalance(), null, null);
    }

    /** Shares change hands with no money, so the balance is never involved. */
    private static TransactionValues split(TransactionRequest request, Map<String, String> errors) {
        String ticker = ticker(request, errors);
        BigDecimal from = requirePositive("splitFrom", request.splitFrom(), errors);
        BigDecimal to = requirePositive("splitTo", request.splitTo(), errors);
        if (errors.isEmpty() && from.compareTo(to) == 0) {
            errors.put("splitTo", "must differ from the old shares");
        }
        if (!errors.isEmpty()) {
            return null;
        }
        return new TransactionValues(request.type(), request.date(), ticker, null, null, null, null, null,
                BigDecimal.ZERO, false, from, to);
    }

    private static String ticker(TransactionRequest request, Map<String, String> errors) {
        if (request.ticker() == null) {
            errors.put("ticker", "must not be null");
            return null;
        }
        return InstrumentService.normalizeTicker(request.ticker());
    }

    private static BigDecimal requirePositive(String field, BigDecimal value, Map<String, String> errors) {
        if (value == null) {
            errors.put(field, "must not be null");
        } else if (value.signum() <= 0) {
            errors.put(field, "must be greater than 0");
        }
        return value;
    }

    private static BigDecimal requireAmount(TransactionRequest request, String currency, Map<String, String> errors) {
        BigDecimal amount = requirePositive("amount", request.amount(), errors);
        return amount == null ? null : checkDecimals("amount", amount, currency, errors);
    }

    private static BigDecimal optionalAmount(String field, BigDecimal value, String currency, Map<String, String> errors) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        if (amount.signum() < 0) {
            errors.put(field, "must not be negative");
        }
        return checkDecimals(field, amount, currency, errors);
    }

    private static BigDecimal checkDecimals(String field, BigDecimal amount, String currency, Map<String, String> errors) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        if (amount.stripTrailingZeros().scale() > digits) {
            errors.put(field, "can have at most " + digits + " decimal places in " + currency);
        }
        return amount;
    }

    private static BigDecimal rounded(BigDecimal amount, String currency) {
        return amount.setScale(Currency.getInstance(currency).getDefaultFractionDigits(), RoundingMode.HALF_UP);
    }
}
