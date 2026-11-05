package io.github.akarp0508.koifolio.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Which fields are needed depends on the type, so that's checked in {@link TransactionService}.
 * Fields the type doesn't use are ignored.
 */
record TransactionRequest(
        @Schema(description = "Needed when creating. An existing transaction can't move to another account.")
        UUID accountId,
        @NotNull TransactionType type,
        @NotNull @Schema(example = "2026-03-12") LocalDate date,
        @Schema(description = "buy, sell, dividend", example = "PAS")
        @Pattern(regexp = "\\s*[A-Za-z0-9.\\-]{1,20}\\s*", message = "must be up to 20 letters, digits, dots or dashes")
        String ticker,
        @Schema(type = "string", description = "buy, sell", example = "10")
        @Digits(integer = 15, fraction = 8, message = "must have at most 15 digits and 8 decimal places")
        BigDecimal quantity,
        @Schema(type = "string", description = "buy, sell. Per share, in the account's currency", example = "120.00")
        @Digits(integer = 13, fraction = 6, message = "must have at most 13 digits and 6 decimal places")
        BigDecimal price,
        @Schema(type = "string", description = "buy, sell. Defaults to 0", example = "0")
        @Digits(integer = 15, fraction = 4, message = "must have at most 15 digits and 4 decimal places")
        BigDecimal fee,
        @Schema(type = "string", description = "deposit, withdrawal, fee, and gross dividend", example = "5000.00")
        @Digits(integer = 15, fraction = 4, message = "must have at most 15 digits and 4 decimal places")
        BigDecimal amount,
        @Schema(type = "string", description = "dividend. Defaults to 0", example = "0")
        @Digits(integer = 15, fraction = 4, message = "must have at most 15 digits and 4 decimal places")
        BigDecimal withheldTax,
        @NotNull @Schema(description = "Whether this changes the account balance. Off for backfilled history.")
        Boolean affectsBalance,
        @Schema(description = "Go ahead even if this takes the balance below zero. Without it, that returns 409.")
        Boolean allowNegativeBalance) {
}
