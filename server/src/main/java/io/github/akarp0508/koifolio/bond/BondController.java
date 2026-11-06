package io.github.akarp0508.koifolio.bond;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.audit.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bonds")
@Tag(name = "Bonds")
class BondController {

    private final BondService bonds;

    BondController(BondService bonds) {
        this.bonds = bonds;
    }

    @GetMapping
    @Operation(summary = "Your Polish retail bonds, valued today", description = """
            TOS, ROS, EDO and ROD. Value follows the Ministry of Finance's interest tables. ifRedeemedToday takes off the \
            early redemption fee, which never exceeds the interest earned, and tax isn't taken off.""")
    List<BondResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return bonds.list(userId(jwt)).stream().map(BondResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a bond purchase", description = """
            With affectsBalance on, the cost of 100 PLN a bond comes out of the account's cash. Accrual starts on \
            purchaseDate. Give one rate for a TOS, and each year's rate so far for the others.""")
    BondResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BondRequest request) {
        return BondResponse.of(bonds.create(userId(jwt), request.input(), Actor.USER));
    }

    @PutMapping("/{id}/rates")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Replace a bond's yearly rates", description = "For bonds whose rate is set again each year.")
    void setRates(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody BondRatesRequest request) {
        bonds.setRates(userId(jwt), id, request.rates());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a bond purchase", description = """
            For a mistake, or once it's redeemed. With affectsBalance on, the cost goes back to the account's cash.""")
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        bonds.delete(userId(jwt), id, Actor.USER);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record BondRequest(
            @NotBlank @Schema(example = "TOS0828") String series,
            @NotNull UUID accountId,
            @NotNull @Schema(description = "Bonds, each 100 PLN", example = "400") Integer quantity,
            @NotNull @Schema(example = "2025-08-14") LocalDate purchaseDate,
            @NotEmpty @Schema(description = "In percent, one per year so far") List<BigDecimal> rates,
            @Schema(type = "string", description = "Per bond. Only a TOS can leave it out") BigDecimal earlyFee,
            @NotNull Boolean affectsBalance) {

        BondService.Input input() {
            return new BondService.Input(series, accountId, quantity, purchaseDate, rates, earlyFee, affectsBalance);
        }
    }

    record BondRatesRequest(@NotEmpty List<BigDecimal> rates) {
    }

    /** @param rateAssumed a year has no rate yet, so the last known one stands in */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BondResponse(UUID id, UUID accountId, String accountName, String series, String kind, int quantity, LocalDate purchaseDate,
                        LocalDate maturityDate, List<String> rates, String earlyFee, String cost, String value, String interest,
                        String ifRedeemedToday, boolean matured, boolean rateAssumed) {

        static BondResponse of(BondService.Valued bond) {
            return new BondResponse(bond.id(), bond.account().getId(), bond.account().getName(), bond.series(), bond.kind(),
                    bond.quantity(), bond.purchaseDate(), bond.maturity(),
                    bond.rates().stream().map(rate -> rate.stripTrailingZeros().toPlainString()).toList(), money(bond.earlyFee()),
                    money(bond.cost()), money(bond.value()), money(bond.interest()), money(bond.ifRedeemedToday()), bond.matured(),
                    bond.rateAssumed());
        }

        private static String money(BigDecimal value) {
            return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
