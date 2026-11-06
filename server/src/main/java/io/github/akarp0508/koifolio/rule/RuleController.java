package io.github.akarp0508.koifolio.rule;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.domain.RuleCheck;
import io.github.akarp0508.koifolio.domain.RuleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
@RequestMapping("/api/v1/rules")
@Tag(name = "Rules")
class RuleController {

    private final RuleService rules;

    RuleController(RuleService rules) {
        this.rules = rules;
    }

    @GetMapping
    @Operation(summary = "List your rules")
    List<RuleResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return rules.list(userId(jwt)).stream().map(RuleResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a rule", description = """
            Without an account it covers everything. A low-risk rule needs maxRisk, the highest risk that counts.""")
    RuleResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RuleRequest request) {
        return RuleResponse.of(rules.create(userId(jwt), request.input()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a rule")
    RuleResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RuleRequest request) {
        return RuleResponse.of(rules.update(userId(jwt), id, request.input()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a rule")
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        rules.delete(userId(jwt), id);
    }

    @GetMapping("/check")
    @Operation(summary = "Check your rules against your holdings now", description = """
            Weights are of the value plus cash. A company in two accounts counts as one on a rule covering \
            everything. Cash counts as low risk, and positions with no risk set don't.""")
    List<RuleCheckResponse> check(@AuthenticationPrincipal Jwt jwt) {
        return rules.check(userId(jwt)).stream().map(RuleCheckResponse::of).toList();
    }

    record RuleRequest(
            @NotNull RuleType type,
            @Schema(description = "Leave out to cover every account") UUID accountId,
            @NotNull @Positive(message = "must be between 0 and 100")
            @DecimalMax(value = "100", message = "must be between 0 and 100")
            @Digits(integer = 3, fraction = 2, message = "must have at most 2 decimal places")
            @Schema(type = "string", example = "20") BigDecimal limitPercent,
            @Min(value = 1, message = "must be between 1 and 5") @Max(value = 5, message = "must be between 1 and 5")
            @Schema(description = "Low-risk rules only: the highest risk that counts as low") Integer maxRisk) {

        RuleService.Input input() {
            return new RuleService.Input(type, accountId, limitPercent, maxRisk);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RuleResponse(UUID id, RuleType type, UUID accountId, String limitPercent, Integer maxRisk) {

        static RuleResponse of(Rule rule) {
            return new RuleResponse(rule.getId(), rule.getType(), rule.getAccountId(),
                    rule.getLimitPercent().stripTrailingZeros().toPlainString(), rule.getMaxRisk());
        }
    }

    /**
     * @param actual the biggest company's weight or the low-risk weight, left out with nothing to check
     * @param breaches the companies over a maximum
     * @param unpriced open positions left out, lacking a price or an FX rate
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RuleCheckResponse(RuleResponse rule, boolean passed, String actual, List<BreachResponse> breaches, int unpriced) {

        static RuleCheckResponse of(RuleService.Checked checked) {
            RuleCheck check = checked.check();
            return new RuleCheckResponse(RuleResponse.of(checked.rule()), check.passed(),
                    check.actual().map(RuleController::percent).orElse(null),
                    check.breaches().stream().map(breach -> new BreachResponse(breach.ticker(), percent(breach.weight()))).toList(),
                    checked.unpriced());
        }
    }

    record BreachResponse(String ticker, String weight) {
    }

    private static String percent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
