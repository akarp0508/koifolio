package io.github.akarp0508.koifolio.position;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.domain.PositionHistory;
import io.github.akarp0508.koifolio.domain.PositionResult;
import io.github.akarp0508.koifolio.domain.Valuation;
import io.github.akarp0508.koifolio.price.PriceService.CurrentPrice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            left out unless closed is on. An incomplete one always shows.""")
    List<PositionResponse> list(@AuthenticationPrincipal Jwt jwt,
                                @RequestParam(required = false) UUID account,
                                @RequestParam(defaultValue = "false") boolean closed) {
        return positions.list(UUID.fromString(jwt.getSubject())).stream()
                .filter(position -> account == null || position.account().getId().equals(account))
                .filter(position -> closed || !position.result().isClosed() || position.result().incomplete())
                .map(PositionResponse::of)
                .toList();
    }

    @GetMapping("/{accountId}/{ticker}/history")
    @Operation(summary = "A position's value on each trading day since its first trade", description = """
            Closes are stored daily and missing days are filled in automatically, so a new or backfilled \
            position can take a few minutes to appear. Prices are adjusted for splits.""")
    HistoryResponse history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId, @PathVariable String ticker) {
        return HistoryResponse.of(positions.history(UUID.fromString(jwt.getSubject()), accountId, ticker));
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
     * @param priceCurrency can differ from {@code currency}; value and gain are then left out
     * @param currencyMismatch the price is in another currency than the account
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PositionResponse(UUID accountId, String ticker, String currency, String quantity, String averageCost,
                            String cost, String realizedGain, boolean closed, boolean incomplete,
                            String price, String priceCurrency, Instant quotedAt, String value,
                            String unrealizedGain, String unrealizedPercent, boolean currencyMismatch) {

        static PositionResponse of(PositionService.Position position) {
            PositionResult result = position.result();
            String currency = position.account().getCurrency();
            String averageCost = result.isClosed() ? null : money(result.averageCost(), currency);
            String quantity = result.quantity().stripTrailingZeros().toPlainString();

            Optional<CurrentPrice> price = position.price().filter(current -> !result.isClosed());
            boolean mismatch = price.isPresent() && !price.get().currency().equals(currency);
            Optional<Valuation> valuation = price.filter(current -> !mismatch)
                    .map(current -> Valuation.of(result, current.price()));

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
                    mismatch);
        }

        static String money(BigDecimal value, String currency) {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return value.setScale(digits, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
