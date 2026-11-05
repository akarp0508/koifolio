package io.github.akarp0508.koifolio.account;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.auth.AuthService;
import io.github.akarp0508.koifolio.domain.CostMethod;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every lookup is scoped to the user, so another user's account simply doesn't exist. */
@Service
class AccountService {

    private final AccountRepository accounts;
    private final AuthService authService;

    AccountService(AccountRepository accounts, AuthService authService) {
        this.accounts = accounts;
        this.authService = authService;
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

    @Transactional
    Account create(UUID userId, String name, String description, String currencyCode, BigDecimal balance,
                   CostMethod costMethod) {
        requireUser(userId);
        Currency currency = parseCurrency(currencyCode);
        BigDecimal amount = balance == null ? BigDecimal.ZERO : balance;
        requireFits(amount, currency);
        return accounts.save(new Account(userId, name.strip(), description == null ? "" : description,
                currency.getCurrencyCode(), amount, costMethod == null ? CostMethod.AVERAGE : costMethod));
    }

    @Transactional
    Account update(UUID userId, UUID id, String name, String description, BigDecimal balance, CostMethod costMethod) {
        requireUser(userId);
        Account account = accounts.findForUpdate(id, userId).orElseThrow(AccountService::notFound);
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
        return account;
    }

    @Transactional
    void delete(UUID userId, UUID id) {
        requireUser(userId);
        accounts.delete(find(userId, id));
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
