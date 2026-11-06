package io.github.akarp0508.koifolio.position;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.domain.Dividend;
import io.github.akarp0508.koifolio.domain.PlanCheck;
import io.github.akarp0508.koifolio.domain.PositionHistory;
import io.github.akarp0508.koifolio.domain.PositionResult;
import io.github.akarp0508.koifolio.domain.Valuation;
import io.github.akarp0508.koifolio.instrument.InstrumentDetails;
import io.github.akarp0508.koifolio.instrument.Tags;
import io.github.akarp0508.koifolio.price.PriceService.CurrentPrice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/positions")
@Tag(name = "Positions")
class PositionController {

    private final PositionService positions;

    PositionController(PositionService positions) {
        this.positions = positions;
    }

    @GetMapping
    @Operation(summary = "List your positions, by ticker", description = """
            Worked out from buys and sells, using each account's cost method. Fully sold positions are \
            left out unless closed is on. An incomplete one always shows. Weights are shares of the value, \
            cash included.""")
    List<PositionResponse> list(@AuthenticationPrincipal Jwt jwt,
                                @RequestParam(required = false) UUID account,
                                @RequestParam(defaultValue = "false") boolean closed) {
        return responses(positions, UUID.fromString(jwt.getSubject()), account, closed);
    }

    static List<PositionResponse> responses(PositionService positions, UUID userId, UUID account, boolean closed) {
        List<PositionService.Position> all = positions.list(userId);
        Map<PositionService.Position, PositionService.Weight> weights = positions.weights(userId, all);
        return all.stream()
                .filter(position -> account == null || position.account().getId().equals(account))
                .filter(position -> closed || !position.result().isClosed() || position.result().incomplete())
                .map(position -> PositionResponse.of(position, weights.getOrDefault(position, PositionService.Weight.NONE)))
                .toList();
    }

    @GetMapping("/{accountId}/{ticker}/history")
    @Operation(summary = "A position's value on each trading day since its first trade", description = """
            Closes are stored daily and missing days are filled in automatically, so a new or backfilled \
            position can take a few minutes to appear. Prices are adjusted for splits.""")
    HistoryResponse history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId, @PathVariable String ticker) {
        return HistoryResponse.of(positions.history(UUID.fromString(jwt.getSubject()), accountId, ticker));
    }

    @GetMapping("/{accountId}/{ticker}/details")
    @Operation(summary = "The company and your plan for a position")
    DetailsResponse details(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId, @PathVariable String ticker) {
        return DetailsResponse.of(positions.one(UUID.fromString(jwt.getSubject()), accountId, ticker));
    }

    @PutMapping("/{accountId}/{ticker}/plan")
    @Operation(summary = "Replace your plan for a position", description = """
            Prices are in the instrument's price currency. A plan stays if you sell out and buy back.""")
    DetailsResponse savePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId, @PathVariable String ticker,
                             @Valid @RequestBody PlanRequest request) {
        return DetailsResponse.of(positions.savePlan(UUID.fromString(jwt.getSubject()), accountId, ticker, request.plan(),
                request.risk(), request.targetPrice(), request.stopPrice(), Tags.clean(request.tags())));
    }

    record PlanRequest(
            @Size(max = 20000, message = "must be at most 20000 characters") String plan,
            @Min(value = 1, message = "must be between 1 and 5") @Max(value = 5, message = "must be between 1 and 5")
            @Schema(description = "Overrides the company's risk for this position") Integer risk,
            @Positive(message = "must be greater than 0")
            @Digits(integer = 13, fraction = 6, message = "must have at most 13 digits and 6 decimal places")
            @Schema(type = "string", example = "180") BigDecimal targetPrice,
            @Positive(message = "must be greater than 0")
            @Digits(integer = 13, fraction = 6, message = "must have at most 13 digits and 6 decimal places")
            @Schema(type = "string", example = "120") BigDecimal stopPrice,
            @Size(max = 20, message = "must be at most 20 tags")
            List<@Size(max = 30, message = "must be at most 30 characters") String> tags) {
    }

    /**
     * @param risk the plan's own risk, not the company's
     * @param targetDistance how far the price must move to the target, in percent; left out without both
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DetailsResponse(UUID instrumentId, String name, String sector, Integer companyRisk, String thesis, String analysis,
                           String plan, Integer risk, String targetPrice, String stopPrice, String targetDistance,
                           String stopDistance, PlanCheck.Signal signal, Set<String> instrumentTags,
                           Set<String> positionTags) {

        static DetailsResponse of(PositionService.Position position) {
            InstrumentDetails company = position.instrument().details();
            Optional<PositionPlan> plan = position.plan();
            Optional<PlanCheck> check = position.planCheck();
            return new DetailsResponse(position.instrument().id(), company.name(), company.sector(), company.risk(),
                    company.thesis(), company.analysis(),
                    plan.map(PositionPlan::getPlan).orElse(""), plan.map(PositionPlan::getRisk).orElse(null),
                    plan.map(PositionPlan::getTargetPrice).map(PositionController::plain).orElse(null),
                    plan.map(PositionPlan::getStopPrice).map(PositionController::plain).orElse(null),
                    check.flatMap(PlanCheck::targetDistance).map(PositionController::percent).orElse(null),
                    check.flatMap(PlanCheck::stopDistance).map(PositionController::percent).orElse(null),
                    check.flatMap(PlanCheck::signal).orElse(null), company.tags(), position.tags());
        }
    }

    /**
     * @param cost at each purchase's rate
     * @param gain value now against that cost
     * @param currencyGain the part of the gain from exchange rates moving
     * @param dividends net of tax withheld, each at its own date's rate
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record InBase(String currency, String cost, String realizedGain, String value, String gain, String currencyGain,
                  String dividends) {

        static InBase of(PositionService.Position position, Optional<Valuation> valuation) {
            String base = position.baseCurrency();
            if (position.account().getCurrency().equals(base) || position.inBase().isEmpty()) {
                return null;
            }
            PositionResult result = position.inBase().get();
            Optional<BigDecimal> value = valuation.flatMap(v -> position.convertToBase(v.value()));
            Optional<BigDecimal> costToday = position.convertToBase(position.result().cost());
            return new InBase(base, PositionResponse.money(result.cost(), base),
                    PositionResponse.money(result.realizedGain(), base),
                    value.map(v -> PositionResponse.money(v, base)).orElse(null),
                    value.map(v -> PositionResponse.money(v.subtract(result.cost()), base)).orElse(null),
                    value.flatMap(v -> costToday).map(today -> PositionResponse.money(today.subtract(result.cost()), base))
                            .orElse(null),
                    PositionResponse.money(Dividend.total(position.dividendsInBase(), Dividend::net), base));
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String percent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * @param covered whether the price source has this ticker at all
     * @param currencyMismatch prices are in another currency than the account, so points have no value
     */
    record HistoryResponse(UUID accountId, String ticker, String currency, boolean covered, boolean currencyMismatch,
                           List<HistoryPoint> points) {

        static HistoryResponse of(PositionService.History history) {
            String currency = history.account().getCurrency();
            boolean mismatch = history.currencyMismatch();
            return new HistoryResponse(history.account().getId(), history.ticker(), currency, history.covered(), mismatch,
                    history.points().stream().map(point -> HistoryPoint.of(point, currency, mismatch)).toList());
        }
    }

    /** @param traded a buy or sell happened since the previous point */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HistoryPoint(LocalDate date, String quantity, String close, String value, String cost, boolean traded) {

        static HistoryPoint of(PositionHistory.Point point, String currency, boolean mismatch) {
            return new HistoryPoint(point.date(), point.quantity().stripTrailingZeros().toPlainString(),
                    point.close().stripTrailingZeros().toPlainString(),
                    mismatch ? null : PositionResponse.money(point.value(), currency),
                    PositionResponse.money(point.cost(), currency), point.traded());
        }
    }

    /**
     * @param averageCost left out once nothing is held
     * @param realizedGain counts only shares with a known cost
     * @param incomplete a sale exceeded what was held, so the history is missing something
     * @param priceCurrency can differ from {@code currency}; value and gain are converted at the latest rate
     * @param currencyMismatch the price is in another currency and there's no rate, so value and gain are left out
     * @param risk the plan's risk if set, otherwise the company's; left out if neither is
     * @param inBase the same in the base currency, left out when the account already uses it
     * @param signal the price is at or near the plan's target or stop
     * @param weight percent of the account, cash included; left out without a value
     * @param portfolioWeight percent of all accounts, cash included, at the latest rates
     * @param dividends received, net of tax withheld
     * @param dividendsWithheld tax withheld from them
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PositionResponse(UUID accountId, String ticker, String currency, String quantity, String averageCost,
                            String cost, String realizedGain, boolean closed, boolean incomplete,
                            String price, String priceCurrency, Instant quotedAt, String value,
                            String unrealizedGain, String unrealizedPercent, boolean currencyMismatch,
                            UUID instrumentId, String sector, Integer risk, PlanCheck.Signal signal,
                            Set<String> instrumentTags, Set<String> positionTags, InBase inBase,
                            String weight, String portfolioWeight, String dividends, String dividendsWithheld) {

        static PositionResponse of(PositionService.Position position, PositionService.Weight weight) {
            PositionResult result = position.result();
            String currency = position.account().getCurrency();
            String averageCost = result.isClosed() ? null : money(result.averageCost(), currency);
            String quantity = result.quantity().stripTrailingZeros().toPlainString();

            Optional<CurrentPrice> price = position.price().filter(current -> !result.isClosed());
            boolean mismatch = price.isPresent() && position.accountPrice().isEmpty();
            Optional<Valuation> valuation = price.flatMap(current -> position.accountPrice())
                    .map(accountPrice -> Valuation.of(result, accountPrice));

            return new PositionResponse(position.account().getId(), position.ticker(), currency, quantity, averageCost,
                    money(result.cost(), currency), money(result.realizedGain(), currency), result.isClosed(),
                    result.incomplete(),
                    price.map(current -> current.price().stripTrailingZeros().toPlainString()).orElse(null),
                    price.map(CurrentPrice::currency).orElse(null),
                    price.map(CurrentPrice::quotedAt).orElse(null),
                    valuation.map(v -> money(v.value(), currency)).orElse(null),
                    valuation.map(v -> money(v.gain(), currency)).orElse(null),
                    valuation.flatMap(Valuation::gainPercent)
                            .map(percent -> percent.setScale(1, RoundingMode.HALF_UP).toPlainString()).orElse(null),
                    mismatch,
                    position.instrument().id(), position.instrument().details().sector(), position.risk(),
                    position.planCheck().flatMap(PlanCheck::signal).orElse(null),
                    position.instrument().details().tags(), position.tags(), InBase.of(position, valuation),
                    weight.ofAccount().map(PositionController::percent).orElse(null),
                    weight.ofPortfolio().map(PositionController::percent).orElse(null),
                    money(Dividend.total(position.dividends(), Dividend::net), currency),
                    money(Dividend.total(position.dividends(), Dividend::withheld), currency));
        }

        static String money(BigDecimal value, String currency) {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return value.setScale(digits, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
