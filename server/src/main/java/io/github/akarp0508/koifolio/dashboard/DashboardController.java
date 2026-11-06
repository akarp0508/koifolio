package io.github.akarp0508.koifolio.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.domain.Breakdown;
import io.github.akarp0508.koifolio.domain.PlanCheck;
import io.github.akarp0508.koifolio.domain.PortfolioHistory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
class DashboardController {

    private final DashboardService dashboards;

    DashboardController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping
    @Operation(summary = "Totals and breakdowns in your base currency", description = """
            Converted at the latest rates. A position matches if it has any of the instrument tags and any of \
            the position tags. Cash has no tags, so it's left out while tags filter the view.""")
    DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt,
                                @RequestParam(required = false) UUID account,
                                @RequestParam(defaultValue = "") Set<String> instrumentTag,
                                @RequestParam(defaultValue = "") Set<String> positionTag) {
        var filter = new DashboardService.Filter(account, instrumentTag, positionTag);
        return DashboardResponse.of(dashboards.build(UUID.fromString(jwt.getSubject()), filter));
    }

    @GetMapping("/history")
    @Operation(summary = "Portfolio value over time, and changes over 1D to 1Y", description = """
            Same filters as the dashboard, positions only. Each day uses that day's FX rates. A change is \
            what the gain moved by, so deposits and buys don't count as profit.""")
    PortfolioHistoryResponse history(@AuthenticationPrincipal Jwt jwt,
                            @RequestParam(required = false) UUID account,
                            @RequestParam(defaultValue = "") Set<String> instrumentTag,
                            @RequestParam(defaultValue = "") Set<String> positionTag) {
        var filter = new DashboardService.Filter(account, instrumentTag, positionTag);
        return PortfolioHistoryResponse.of(dashboards.history(UUID.fromString(jwt.getSubject()), filter));
    }

    record PortfolioHistoryResponse(String currency, List<PortfolioHistoryPoint> points, List<PortfolioChangeResponse> changes) {

        static PortfolioHistoryResponse of(DashboardService.History history) {
            String currency = history.currency();
            return new PortfolioHistoryResponse(currency,
                    history.points().stream().map(point -> new PortfolioHistoryPoint(point.date(),
                            DashboardResponse.money(point.value(), currency), DashboardResponse.money(point.cost(), currency),
                            DashboardResponse.money(point.gain(), currency))).toList(),
                    history.changes().stream().map(change -> new PortfolioChangeResponse(change.period(),
                            DashboardResponse.money(change.change(), currency),
                            change.percent().map(DashboardResponse::percent).orElse(null))).toList());
        }
    }

    /** @param gain value minus cost, plus what's been realized */
    record PortfolioHistoryPoint(LocalDate date, String value, String cost, String gain) {
    }

    /** @param percent of the value at the start; left out if nothing was held then */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PortfolioChangeResponse(PortfolioHistory.Period period, String change, String percent) {
    }

    /**
     * @param cash left out while tags filter the view
     * @param currencyGain the part of unrealizedGain from exchange rates, for accounts in another currency
     * @param unpriced open positions left out of the numbers, lacking a price or an FX rate
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DashboardResponse(String currency, String value, String positionsValue, String unrealizedGain,
                             String currencyGain, String unrealizedPercent, String realizedGain, String cash, List<SliceResponse> bySector,
                             List<SliceResponse> byRisk, List<AttentionResponse> attention, int unpriced) {

        static DashboardResponse of(DashboardService.Dashboard d) {
            String currency = d.currency();
            return new DashboardResponse(currency, money(d.value(), currency), money(d.positionsValue(), currency),
                    money(d.unrealizedGain(), currency), money(d.currencyGain(), currency), d.gainPercent().map(DashboardResponse::percent).orElse(null),
                    money(d.realizedGain(), currency), d.cash().map(cash -> money(cash, currency)).orElse(null),
                    slices(d.bySector(), currency), slices(d.byRisk(), currency),
                    d.attention().stream().map(AttentionResponse::of).toList(), d.unpriced());
        }

        private static List<SliceResponse> slices(List<Breakdown.Slice> slices, String currency) {
            return slices.stream().map(slice -> new SliceResponse(slice.label().orElse(null),
                    money(slice.value(), currency), percent(slice.percent()))).toList();
        }

        static String percent(BigDecimal value) {
            return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
        }

        static String money(BigDecimal value, String currency) {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return value.setScale(digits, RoundingMode.HALF_UP).toPlainString();
        }
    }

    /** @param label left out for positions with no sector or risk set */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SliceResponse(String label, String value, String percent) {
    }

    /** @param distance how far the price is from the level in question, in percent */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AttentionResponse(UUID accountId, String accountName, String ticker, PlanCheck.Signal signal, String distance) {

        static AttentionResponse of(DashboardService.Attention attention) {
            PlanCheck check = attention.check();
            PlanCheck.Signal signal = check.signal().orElseThrow();
            boolean stop = signal == PlanCheck.Signal.NEAR_STOP || signal == PlanCheck.Signal.STOP_HIT;
            Optional<BigDecimal> distance = stop ? check.stopDistance() : check.targetDistance();
            return new AttentionResponse(attention.position().account().getId(), attention.position().account().getName(),
                    attention.position().ticker(), signal, distance.map(DashboardResponse::percent).orElse(null));
        }
    }
}
