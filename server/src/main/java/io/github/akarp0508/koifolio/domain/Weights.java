package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Each value's percent of a total that also holds cash. */
public final class Weights {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Weights() {
    }

    /** Negative cash shrinks the total, so a borrowed position can pass 100%. Without a positive total there are none. */
    public static <K> Map<K, BigDecimal> of(Map<K, BigDecimal> values, BigDecimal cash) {
        BigDecimal total = values.values().stream()
                .reduce(cash, (a, b) -> a.add(b, PositionCalculator.PRECISION));
        Map<K, BigDecimal> weights = new LinkedHashMap<>();
        if (total.signum() > 0) {
            values.forEach((key, value) -> weights.put(key,
                    value.multiply(HUNDRED, PositionCalculator.PRECISION).divide(total, PositionCalculator.PRECISION)));
        }
        return weights;
    }
}
