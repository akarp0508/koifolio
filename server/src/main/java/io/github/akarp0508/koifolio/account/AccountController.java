package io.github.akarp0508.koifolio.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
class AccountController {

    private static final String DIGITS_MESSAGE = "must have at most 15 digits and 4 decimal places";

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    @Operation(summary = "List your accounts")
    List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accounts.list(userId(jwt)).stream().map(AccountResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account", description = "The balance defaults to 0. The currency can't be changed later.")
    AccountResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccountRequest request) {
        return AccountResponse.of(accounts.create(userId(jwt), request.name(), request.description(),
                request.currency(), request.balance()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one account")
    AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return AccountResponse.of(accounts.get(userId(jwt), id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change an account", description = "Send only the fields to change.")
    AccountResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                           @Valid @RequestBody UpdateAccountRequest request) {
        return AccountResponse.of(accounts.update(userId(jwt), id, request.name(), request.description(),
                request.balance()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an account", description = "Also deletes its transactions.")
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        accounts.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record CreateAccountRequest(
            @NotBlank @Size(max = 100, message = "must be at most 100 characters") String name,
            @Size(max = 2000, message = "must be at most 2000 characters") String description,
            @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter code like PLN")
            @Schema(example = "PLN") String currency,
            @Digits(integer = 15, fraction = 4, message = DIGITS_MESSAGE)
            @Schema(type = "string", example = "5000.00") BigDecimal balance) {
    }

    record UpdateAccountRequest(
            @Pattern(regexp = ".*\\S.*", message = "must not be blank")
            @Size(max = 100, message = "must be at most 100 characters") String name,
            @Size(max = 2000, message = "must be at most 2000 characters") String description,
            @Digits(integer = 15, fraction = 4, message = DIGITS_MESSAGE)
            @Schema(type = "string", example = "5000.00") BigDecimal balance) {
    }

    /** @param balanceSetOn the last day the balance was set by hand */
    record AccountResponse(UUID id, String name, String description, String currency,
                           @Schema(example = "5000.00") String balance, LocalDate balanceSetOn) {

        static AccountResponse of(Account account) {
            int digits = Currency.getInstance(account.getCurrency()).getDefaultFractionDigits();
            String balance = account.getBalance().setScale(digits, RoundingMode.UNNECESSARY).toPlainString();
            return new AccountResponse(account.getId(), account.getName(), account.getDescription(),
                    account.getCurrency(), balance, account.getBalanceSetOn());
        }
    }
}
