package io.github.akarp0508.koifolio.transaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.audit.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions")
class TransactionController {

    private final TransactionService transactions;

    TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @GetMapping
    @Operation(summary = "List your transactions, newest first")
    List<TransactionResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) UUID account,
                                   @RequestParam(defaultValue = "false") boolean unvalidated) {
        return transactions.list(userId(jwt), account, unvalidated).stream().map(TransactionResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a transaction", description = """
            Amounts are in the account's currency. With affectsBalance on, the account balance follows \
            the transaction. A sale of more than is held is saved, with oversold set.""")
    TransactionResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.of(transactions.create(userId(jwt), request, Actor.USER));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one transaction")
    TransactionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return TransactionResponse.of(transactions.get(userId(jwt), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a transaction", description = "The balance follows the change. accountId is ignored.")
    TransactionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.of(transactions.update(userId(jwt), id, request, Actor.USER));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a transaction", description = """
            Undoes its effect on the balance. It stays in the database and the change log, but out of every number.""")
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        transactions.delete(userId(jwt), id, Actor.USER);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a transaction an agent added", description = """
            It already counts, so this only clears the mark.""")
    TransactionResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return TransactionResponse.of(transactions.approve(userId(jwt), id));
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reject a transaction an agent added", description = """
            Undoes its effect on the balance and takes it out of every number.""")
    void reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        transactions.reject(userId(jwt), id);
    }


    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /**
     * Fields a type doesn't use are left out.
     *
     * @param cashEffect what the transaction does to cash, signed
     * @param oversold a sale of more than was held at that point
     * @param createdBy the agent that added it, left out for you
     * @param unvalidated added by an agent and waiting for your review; it counts in the meantime
     * @param importId shared by what an agent added in one call
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TransactionResponse(UUID id, UUID accountId, TransactionType type, LocalDate date, String currency,
                               String ticker, String quantity, String price, String fee, String amount,
                               String withheldTax, String splitFrom, String splitTo, String parentTicker, String costShare,
                               String note, String cashEffect,
                               boolean affectsBalance, boolean oversold, String createdBy, boolean unvalidated,
                               UUID importId, Instant createdAt) {

        static TransactionResponse of(TransactionService.Listed listed) {
            Transaction t = listed.transaction();
            String currency = listed.currency();
            return new TransactionResponse(t.getId(), t.getAccountId(), t.getType(), t.getDate(), currency, listed.ticker(),
                    plain(t.getQuantity()), plain(t.getPrice()), money(t.getFee(), currency), money(t.getAmount(), currency),
                    money(t.getWithheldTax(), currency), plain(t.getSplitFrom()), plain(t.getSplitTo()), listed.parentTicker(), plain(t.getCostShare()), t.getNote(),
                    money(t.getCashEffect(), currency),
                    t.isAffectsBalance(), listed.oversold(), t.getCreatedBy(), t.isUnvalidated(), t.getImportId(), t.getCreatedAt());
        }

        private static String plain(BigDecimal value) {
            return value == null ? null : value.stripTrailingZeros().toPlainString();
        }

        private static String money(BigDecimal value, String currency) {
            if (value == null) {
                return null;
            }
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return value.setScale(digits, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
