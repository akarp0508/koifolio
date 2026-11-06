package io.github.akarp0508.koifolio.transaction;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.FieldValidationException;
import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.domain.Trade;
import io.github.akarp0508.koifolio.instrument.InstrumentService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every lookup is scoped to the user. Writes lock the account row, so concurrent transactions
 * can't lose a balance update.
 */
@Service
public class TransactionService {

    private static final Comparator<Transaction> HISTORY_ORDER =
            Comparator.comparing(Transaction::getDate).thenComparing(Transaction::getCreatedAt);

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final AuthService authService;
    private final InstrumentService instruments;
    private final Validator validator;

    TransactionService(TransactionRepository transactions, AccountRepository accounts, AuthService authService,
                       InstrumentService instruments, Validator validator) {
        this.validator = validator;
        this.transactions = transactions;
        this.accounts = accounts;
        this.authService = authService;
        this.instruments = instruments;
    }

    record Listed(Transaction transaction, String currency, String ticker, boolean oversold) {
    }

    /** A transaction for backup, with no ids of its own. */
    public record Exported(UUID accountId, TransactionType type, LocalDate date, String ticker, BigDecimal quantity,
                           BigDecimal price, BigDecimal fee, BigDecimal amount, BigDecimal withheldTax,
                           BigDecimal splitFrom, BigDecimal splitTo, boolean affectsBalance, Instant createdAt) {
    }

    /** Oldest first. */
    @Transactional(readOnly = true)
    public List<Exported> exportAll(UUID userId) {
        Map<UUID, String> tickers = instruments.views(userId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().ticker()));
        return transactions.findByUserId(userId).stream().sorted(HISTORY_ORDER)
                .map(t -> new Exported(t.getAccountId(), t.getType(), t.getDate(), tickers.get(t.getInstrumentId()),
                        t.getQuantity(), t.getPrice(), t.getFee(), t.getAmount(), t.getWithheldTax(), t.getSplitFrom(),
                        t.getSplitTo(), t.isAffectsBalance(), t.getCreatedAt()))
                .toList();
    }

    /** Checks it as a new transaction would be, but leaves the balance alone, because the export has that as it stood. */
    @Transactional
    public void restore(UUID userId, Exported exported) {
        Account account = accounts.findByIdAndUserId(exported.accountId(), userId).orElseThrow();
        TransactionRequest request = new TransactionRequest(exported.accountId(), exported.type(), exported.date(),
                exported.ticker(), exported.quantity(), exported.price(), exported.fee(), exported.amount(),
                exported.withheldTax(), exported.splitFrom(), exported.splitTo(), exported.affectsBalance(), true);
        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            ConstraintViolation<TransactionRequest> first = violations.iterator().next();
            throw new FieldValidationException(Map.of(first.getPropertyPath().toString(), first.getMessage()));
        }
        TransactionValues values = validate(request, account.getCurrency());
        Transaction transaction = new Transaction(userId, account.getId());
        transaction.apply(values, instrumentFor(userId, values));
        transaction.setCreatedAt(exported.createdAt());
        transactions.save(transaction);
    }

    @Transactional(readOnly = true)
    List<Listed> list(UUID userId, UUID accountId) {
        authService.currentUser(userId);
        List<Transaction> all = transactions.findByUserId(userId);
        Set<UUID> oversold = oversold(all);
        Map<UUID, String> currencies = currencies(userId);
        Map<UUID, String> tickers = instruments.tickers(userId);
        return all.stream()
                .filter(transaction -> accountId == null || transaction.getAccountId().equals(accountId))
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
    Listed create(UUID userId, TransactionRequest request) {
        authService.currentUser(userId);
        if (request.accountId() == null) {
            throw new FieldValidationException(Map.of("accountId", "must not be null"));
        }
        Account account = lockAccount(userId, request.accountId());
        TransactionValues values = validate(request, account.getCurrency());
        Transaction transaction = new Transaction(userId, account.getId());
        BigDecimal balanceBefore = account.getBalance();
        transaction.apply(values, instrumentFor(userId, values));
        account.adjustBalance(transaction.balanceEffect());
        requireAcceptableBalance(account, balanceBefore, request);
        return listed(userId, transactions.save(transaction));
    }

    @Transactional
    Listed update(UUID userId, UUID id, TransactionRequest request) {
        authService.currentUser(userId);
        Transaction transaction = find(userId, id);
        Account account = lockAccount(userId, transaction.getAccountId());
        TransactionValues values = validate(request, account.getCurrency());
        BigDecimal balanceBefore = account.getBalance();
        account.adjustBalance(transaction.balanceEffect().negate());
        transaction.apply(values, instrumentFor(userId, values));
        account.adjustBalance(transaction.balanceEffect());
        requireAcceptableBalance(account, balanceBefore, request);
        return listed(userId, transaction);
    }

    @Transactional
    void delete(UUID userId, UUID id) {
        authService.currentUser(userId);
        Transaction transaction = find(userId, id);
        Account account = lockAccount(userId, transaction.getAccountId());
        account.adjustBalance(transaction.balanceEffect().negate());
        requireBalanceInRange(account);
        transactions.delete(transaction);
    }

    /** Adds a split, as confirmed by the user. */
    @Transactional
    public void addSplit(UUID userId, UUID accountId, String ticker, LocalDate date, BigDecimal from, BigDecimal to) {
        create(userId, new TransactionRequest(accountId, TransactionType.SPLIT, date, ticker, null, null, null, null, null,
                from, to, false, null));
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
