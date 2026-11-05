package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;

/**
 * What a history of trades adds up to.
 *
 * @param cost what the shares still held cost, fees included
 * @param realizedGain gains and losses on sales, counting only shares with a known cost
 * @param incomplete a sale exceeded what was held, so the history is missing something
 */
public record PositionResult(BigDecimal quantity, BigDecimal cost, BigDecimal realizedGain, boolean incomplete) {

    public boolean isClosed() {
        return quantity.signum() == 0;
    }

    /** Only meaningful while shares are held. */
    public BigDecimal averageCost() {
        return cost.divide(quantity, PositionCalculator.PRECISION);
    }
}
