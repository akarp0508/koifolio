package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;

/**
 * Something that changes a holding. {@code fee} is in the same currency as {@code price}. A split
 * gives {@code splitTo} shares for every {@code splitFrom}, so 10 for 1 and 1 for 3 both stay exact.
 * A spin-off takes {@code costShare} percent of the cost away and leaves the shares alone. The new
 * company's side of it is a buy at the cost it took.
 */
public record Trade(Side side, BigDecimal quantity, BigDecimal price, BigDecimal fee, BigDecimal splitFrom,
                    BigDecimal splitTo, BigDecimal costShare) {

    public enum Side {
        BUY,
        SELL,
        SPLIT,
        SPIN_OFF
    }

    public Trade(Side side, BigDecimal quantity, BigDecimal price, BigDecimal fee) {
        this(side, quantity, price, fee, null, null, null);
    }

    public static Trade split(BigDecimal from, BigDecimal to) {
        return new Trade(Side.SPLIT, null, null, null, from, to, null);
    }

    public static Trade spinOff(BigDecimal costShare) {
        return new Trade(Side.SPIN_OFF, null, null, null, null, null, costShare);
    }

    /** Whether this has money to convert. Splits and spin-offs only change what's held. */
    public boolean hasMoney() {
        return price != null;
    }

    /** This trade with its money in another currency. */
    public Trade withAmounts(BigDecimal newPrice, BigDecimal newFee) {
        return new Trade(side, quantity, newPrice, newFee, splitFrom, splitTo, costShare);
    }

    /** What a quantity becomes after this split. */
    BigDecimal applySplit(BigDecimal shares) {
        return shares.multiply(splitTo, PositionCalculator.PRECISION).divide(splitFrom, PositionCalculator.PRECISION);
    }
}
