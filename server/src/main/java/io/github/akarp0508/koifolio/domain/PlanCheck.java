package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * How a price sits against a plan's target and stop. Distances are how far the price would have
 * to move, in percent of the price: positive up, negative down.
 */
public record PlanCheck(Optional<BigDecimal> targetDistance, Optional<BigDecimal> stopDistance, Optional<Signal> signal) {

    /** A price within this percentage of a level counts as near it. */
    private static final BigDecimal NEAR_PERCENT = new BigDecimal("5");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public enum Signal {
        STOP_HIT,
        TARGET_REACHED,
        NEAR_STOP,
        NEAR_TARGET
    }

    public static PlanCheck of(BigDecimal price, Optional<BigDecimal> target, Optional<BigDecimal> stop) {
        Optional<BigDecimal> targetDistance = target.map(level -> distance(price, level));
        Optional<BigDecimal> stopDistance = stop.map(level -> distance(price, level));
        return new PlanCheck(targetDistance, stopDistance, signal(price, target, stop, targetDistance, stopDistance));
    }

    private static BigDecimal distance(BigDecimal price, BigDecimal level) {
        return level.subtract(price, PositionCalculator.PRECISION)
                .multiply(HUNDRED, PositionCalculator.PRECISION)
                .divide(price, PositionCalculator.PRECISION);
    }

    /** The stop comes first: it's the one that shouldn't be missed. */
    private static Optional<Signal> signal(BigDecimal price, Optional<BigDecimal> target, Optional<BigDecimal> stop,
                                           Optional<BigDecimal> targetDistance, Optional<BigDecimal> stopDistance) {
        if (stop.isPresent() && price.compareTo(stop.get()) <= 0) {
            return Optional.of(Signal.STOP_HIT);
        }
        if (target.isPresent() && price.compareTo(target.get()) >= 0) {
            return Optional.of(Signal.TARGET_REACHED);
        }
        if (stopDistance.isPresent() && stopDistance.get().abs().compareTo(NEAR_PERCENT) <= 0) {
            return Optional.of(Signal.NEAR_STOP);
        }
        if (targetDistance.isPresent() && targetDistance.get().abs().compareTo(NEAR_PERCENT) <= 0) {
            return Optional.of(Signal.NEAR_TARGET);
        }
        return Optional.empty();
    }
}
