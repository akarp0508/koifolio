package io.github.akarp0508.koifolio.position;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.domain.PositionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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

    /**
     * @param averageCost left out once nothing is held
     * @param realizedGain counts only shares with a known cost
     * @param incomplete a sale exceeded what was held, so the history is missing something
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PositionResponse(UUID accountId, String ticker, String currency, String quantity, String averageCost,
                            String cost, String realizedGain, boolean closed, boolean incomplete) {

        static PositionResponse of(PositionService.Position position) {
            PositionResult result = position.result();
            String currency = position.account().getCurrency();
            String averageCost = result.isClosed() ? null : money(result.averageCost(), currency);
            String quantity = result.quantity().stripTrailingZeros().toPlainString();
            return new PositionResponse(position.account().getId(), position.ticker(), currency, quantity, averageCost,
                    money(result.cost(), currency), money(result.realizedGain(), currency), result.isClosed(),
                    result.incomplete());
        }

        private static String money(BigDecimal value, String currency) {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return value.setScale(digits, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
