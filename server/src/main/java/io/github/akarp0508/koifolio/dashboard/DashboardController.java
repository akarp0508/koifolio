package io.github.akarp0508.koifolio.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.domain.Breakdown;
import io.github.akarp0508.koifolio.domain.PlanCheck;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * @param cash left out while tags filter the view
     * @param unpriced open positions left out of the numbers, lacking a price or an FX rate
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DashboardResponse(String currency, String value, String positionsValue, String unrealizedGain,
                             String unrealizedPercent, String realizedGain, String cash, List<SliceResponse> bySector,
                             List<SliceResponse> byRisk, List<AttentionResponse> attention, int unpriced) {

        static DashboardResponse of(DashboardService.Dashboard d) {
            String currency = d.currency();
            return new DashboardResponse(currency, money(d.value(), currency), money(d.positionsValue(), currency),
                    money(d.unrealizedGain(), currency), d.gainPercent().map(DashboardResponse::percent).orElse(null),
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

        private static String money(BigDecimal value, String currency) {
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
