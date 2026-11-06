package io.github.akarp0508.koifolio.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.audit.Actor;
import io.github.akarp0508.koifolio.audit.AuditLog;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.domain.CostMethod;
import io.github.akarp0508.koifolio.position.PositionService;
import io.github.akarp0508.koifolio.rule.RuleService;
import io.github.akarp0508.koifolio.transaction.TransactionService;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every lookup is scoped to the user, so another user's account simply doesn't exist. */
@Service
public class AccountService {

    private final AccountRepository accounts;
    private final AuthService authService;
    private final AuditLog auditLog;
    private final TransactionService transactions;
    private final PositionService positions;
    private final RuleService rules;

    AccountService(AccountRepository accounts, AuthService authService, AuditLog auditLog, TransactionService transactions,
                   PositionService positions, RuleService rules) {
        this.accounts = accounts;
        this.authService = authService;
        this.auditLog = auditLog;
        this.transactions = transactions;
        this.positions = positions;
        this.rules = rules;
    }

    /** A new account. @param balance 0 if null @param costMethod average if null */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Input(String name, String description, String currency, BigDecimal balance, CostMethod costMethod,
                        BigDecimal taxRate) {
    }

    @Transactional(readOnly = true)
    List<Account> list(UUID userId) {
        requireUser(userId);
        return accounts.findByUserIdOrderByCreatedAtAscIdAsc(userId);
    }

    @Transactional(readOnly = true)
    Account get(UUID userId, UUID id) {
        requireUser(userId);
        return find(userId, id);
    }

    /** Refuses what {@link #create} would, so a proposal fails early instead of at approval. */
    public void check(Input input) {
        requireFits(input.balance() == null ? BigDecimal.ZERO : input.balance(), parseCurrency(input.currency()));
    }

    @Transactional
    public Account create(UUID userId, Input input, Actor actor) {
        requireUser(userId);
        check(input);
        Account account = new Account(userId, input.name().strip(), input.description() == null ? "" : input.description(),
                parseCurrency(input.currency()).getCurrencyCode(), input.balance() == null ? BigDecimal.ZERO : input.balance(),
                input.costMethod() == null ? CostMethod.AVERAGE : input.costMethod());
        account.setTaxRate(input.taxRate());
        accounts.save(account);
        log(userId, actor, account, "create", "Added account " + account.getName(), null, snapshot(account));
        return account;
    }

    /** Undoes an agent's account. Deleting one erases its transactions, so one that has any stays. */
    @Transactional
    public void deleteUnused(UUID userId, UUID id) {
        if (transactions.exportAll(userId).stream().anyMatch(found -> id.equals(found.accountId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "account_in_use",
                    "This account has transactions, so it can't be reverted. You can delete it from Accounts, which erases them.");
        }
        delete(userId, id);
    }

    @Transactional
    Account update(UUID userId, UUID id, String name, String description, BigDecimal balance, CostMethod costMethod,
                   BigDecimal taxRate, boolean clearTaxRate) {
        requireUser(userId);
        Account account = accounts.findForUpdate(id, userId).orElseThrow(AccountService::notFound);
        Map<String, Object> before = snapshot(account);
        if (name != null) {
            account.setName(name.strip());
        }
        if (description != null) {
            account.setDescription(description);
        }
        if (balance != null) {
            requireFits(balance, Currency.getInstance(account.getCurrency()));
            account.setBalance(balance);
        }
        if (costMethod != null) {
            account.setCostMethod(costMethod);
        }
        if (clearTaxRate) {
            account.setTaxRate(null);
        } else if (taxRate != null) {
            account.setTaxRate(taxRate);
        }
        Map<String, Object> after = snapshot(account);
        if (!after.equals(before)) {
            log(userId, Actor.USER, account, "update", "Changed account " + account.getName(), before, after);
        }
        return account;
    }

    @Transactional
    void delete(UUID userId, UUID id) {
        requireUser(userId);
        Account account = find(userId, id);
        List<?> itsTransactions = transactions.exportAll(userId).stream().filter(found -> id.equals(found.accountId())).toList();
        Map<String, Object> before = snapshot(account);
        before.put("transactions", itsTransactions);
        before.put("plans", positions.exportPlans(userId).stream().filter(plan -> id.equals(plan.accountId())).toList());
        before.put("rules", rules.exportAll(userId).stream().filter(rule -> id.equals(rule.accountId())).toList());
        log(userId, Actor.USER, account, "delete", "Deleted account %s with %d transactions".formatted(account.getName(), itsTransactions.size()),
                before, null);
        accounts.delete(account);
    }

    private void log(UUID userId, Actor actor, Account account, String action, String summary, Map<String, Object> before,
                     Map<String, Object> after) {
        auditLog.record(userId, actor, new AuditLog.Change("account", account.getId(), action, summary, before, after));
    }

    /** Trailing zeros are dropped, so an edit that changes nothing compares as equal. */
    private static Map<String, Object> snapshot(Account account) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", account.getName());
        values.put("description", account.getDescription());
        values.put("currency", account.getCurrency());
        values.put("balance", account.getBalance().stripTrailingZeros());
        values.put("costMethod", account.getCostMethod());
        values.put("taxRate", account.getTaxRate() == null ? null : account.getTaxRate().stripTrailingZeros());
        values.values().removeIf(Objects::isNull);
        return values;
    }

    private Account find(UUID userId, UUID id) {
        return accounts.findByIdAndUserId(id, userId).orElseThrow(AccountService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "account_not_found", "Account not found");
    }

    private void requireUser(UUID userId) {
        authService.currentUser(userId);
    }

    private static Currency parseCurrency(String code) {
        try {
            Currency currency = Currency.getInstance(code);
            if (currency.getDefaultFractionDigits() >= 0) {
                return currency;
            }
        } catch (IllegalArgumentException e) {
            // Reported below, same as a pseudo-currency like XXX
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "unknown_currency", "Unknown currency: " + code);
    }

    private static void requireFits(BigDecimal amount, Currency currency) {
        int digits = currency.getDefaultFractionDigits();
        if (amount.stripTrailingZeros().scale() > digits) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_amount",
                    currency.getCurrencyCode() + " amounts can have at most " + digits + " decimal places");
        }
    }
}
