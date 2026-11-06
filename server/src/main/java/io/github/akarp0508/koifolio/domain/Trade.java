package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;

/**
 * Something that changes a holding. {@code fee} is in the same currency as {@code price}. A split
 * gives {@code splitTo} shares for every {@code splitFrom}, so 10 for 1 and 1 for 3 both stay exact.
 */
public record Trade(Side side, BigDecimal quantity, BigDecimal price, BigDecimal fee, BigDecimal splitFrom,
                    BigDecimal splitTo) {

    public enum Side {
        BUY,
        SELL,
        SPLIT
    }

    public Trade(Side side, BigDecimal quantity, BigDecimal price, BigDecimal fee) {
        this(side, quantity, price, fee, null, null);
    }

    public static Trade split(BigDecimal from, BigDecimal to) {
        return new Trade(Side.SPLIT, null, null, null, from, to);
    }

    /** This trade with its money in another currency. A split has none. */
    public Trade withAmounts(BigDecimal newPrice, BigDecimal newFee) {
        return new Trade(side, quantity, newPrice, newFee, splitFrom, splitTo);
    }

    /** What a quantity becomes after this split. */
    BigDecimal applySplit(BigDecimal shares) {
        return shares.multiply(splitTo, PositionCalculator.PRECISION).divide(splitFrom, PositionCalculator.PRECISION);
    }
}
