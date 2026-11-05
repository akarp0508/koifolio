package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;

/** A buy or sell of one instrument. {@code fee} is in the same currency as {@code price}. */
public record Trade(Side side, BigDecimal quantity, BigDecimal price, BigDecimal fee) {

    public enum Side {
        BUY,
        SELL
    }
}
