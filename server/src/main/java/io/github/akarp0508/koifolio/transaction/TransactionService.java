package io.github.akarp0508.koifolio.transaction;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.FieldValidationException;
import io.github.akarp0508.koifolio.account.Account;
import io.github.akarp0508.koifolio.account.AccountRepository;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.instrument.InstrumentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every lookup is scoped to the user. Writes lock the account row, so concurrent transactions
 * can't lose a balance update.
 */
@Service
class TransactionService {

    private static final Comparator<Transaction> HISTORY_ORDER =
            Comparator.comparing(Transaction::getDate).thenComparing(Transaction::getCreatedAt);

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final AuthService authService;
    private final InstrumentService instruments;

    TransactionService(TransactionRepository transactions, AccountRepository accounts, AuthService authService,
                       InstrumentService instruments) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.authService = authService;
        this.instruments = instruments;
    }

    record Listed(Transaction transaction, boolean oversold) {
    }

    @Transactional(readOnly = true)
    List<Listed> list(UUID userId, UUID accountId) {
        authService.currentUser(userId);
        List<Transaction> all = transactions.findByUserId(userId);
        Set<UUID> oversold = oversold(all);
        return all.stream()
                .filter(transaction -> accountId == null || transaction.getAccountId().equals(accountId))
                .sorted(HISTORY_ORDER.reversed())
                .map(transaction -> new Listed(transaction, oversold.contains(transaction.getId())))
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
        transaction.apply(values);
        account.adjustBalance(transaction.balanceEffect());
        requireAcceptableBalance(account, balanceBefore, request);
        transactions.save(transaction);
        trackTicker(userId, values);
        return listed(userId, transaction);
    }

    @Transactional
    Listed update(UUID userId, UUID id, TransactionRequest request) {
        authService.currentUser(userId);
        Transaction transaction = find(userId, id);
        Account account = lockAccount(userId, transaction.getAccountId());
        TransactionValues values = validate(request, account.getCurrency());
        BigDecimal balanceBefore = account.getBalance();
        account.adjustBalance(transaction.balanceEffect().negate());
        transaction.apply(values);
        account.adjustBalance(transaction.balanceEffect());
        requireAcceptableBalance(account, balanceBefore, request);
        trackTicker(userId, values);
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

    /** The account currency's decimal places, for showing amounts. */
    @Transactional(readOnly = true)
    Map<UUID, String> currencies(UUID userId) {
        Map<UUID, String> currencies = new HashMap<>();
        accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId)
                .forEach(account -> currencies.put(account.getId(), account.getCurrency()));
        return currencies;
    }

    private Listed listed(UUID userId, Transaction transaction) {
        transactions.flush();
        return new Listed(transaction, oversold(transactions.findByUserId(userId)).contains(transaction.getId()));
    }

    private Transaction find(UUID userId, UUID id) {
        return transactions.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "transaction_not_found", "Transaction not found"));
    }

    private Account lockAccount(UUID userId, UUID accountId) {
        return accounts.findForUpdate(accountId, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "account_not_found", "Account not found"));
    }

    private void trackTicker(UUID userId, TransactionValues values) {
        if (values.ticker() != null) {
            instruments.addIfMissing(userId, values.ticker());
        }
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
        record Holding(UUID account, String ticker) {
        }

        Map<Holding, BigDecimal> held = new HashMap<>();
        Set<UUID> oversold = new HashSet<>();
        all.stream().sorted(HISTORY_ORDER).forEach(transaction -> {
            Holding holding = new Holding(transaction.getAccountId(), transaction.getTicker());
            if (transaction.getType() == TransactionType.BUY) {
                held.merge(holding, transaction.getQuantity(), BigDecimal::add);
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
                rounded(cashEffect, currency), request.affectsBalance());
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
                amount.subtract(withheldTax), request.affectsBalance());
    }

    private static TransactionValues cash(TransactionRequest request, String currency, Map<String, String> errors) {
        BigDecimal amount = requireAmount(request, currency, errors);
        if (!errors.isEmpty()) {
            return null;
        }
        BigDecimal cashEffect = request.type() == TransactionType.DEPOSIT ? amount : amount.negate();
        return new TransactionValues(request.type(), request.date(), null, null, null, null, amount, null,
                cashEffect, request.affectsBalance());
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
