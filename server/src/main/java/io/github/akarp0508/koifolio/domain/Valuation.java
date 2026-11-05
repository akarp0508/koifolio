package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * A position at a price in the same currency as its cost.
 *
 * @param gainPercent empty when the shares cost nothing, so there's no base to compare with
 */
public record Valuation(BigDecimal value, BigDecimal gain, Optional<BigDecimal> gainPercent) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static Valuation of(PositionResult position, BigDecimal price) {
        BigDecimal value = position.quantity().multiply(price, PositionCalculator.PRECISION);
        BigDecimal gain = value.subtract(position.cost(), PositionCalculator.PRECISION);
        Optional<BigDecimal> percent = position.cost().signum() == 0
                ? Optional.empty()
                : Optional.of(gain.multiply(HUNDRED, PositionCalculator.PRECISION)
                        .divide(position.cost(), PositionCalculator.PRECISION));
        return new Valuation(value, gain, percent);
    }
}
