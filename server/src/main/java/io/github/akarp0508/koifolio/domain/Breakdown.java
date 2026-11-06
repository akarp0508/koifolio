package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Splits a total by a label, biggest first. A missing label stays its own group, last. */
public final class Breakdown {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Breakdown() {
    }

    public record Part(Optional<String> label, BigDecimal value) {
    }

    public record Slice(Optional<String> label, BigDecimal value, BigDecimal percent) {
    }

    public static List<Slice> of(List<Part> parts) {
        Map<Optional<String>, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Part part : parts) {
            totals.merge(part.label(), part.value(), (a, b) -> a.add(b, PositionCalculator.PRECISION));
            total = total.add(part.value(), PositionCalculator.PRECISION);
        }

        List<Slice> slices = new ArrayList<>();
        for (Map.Entry<Optional<String>, BigDecimal> entry : totals.entrySet()) {
            BigDecimal percent = total.signum() == 0 ? BigDecimal.ZERO
                    : entry.getValue().multiply(HUNDRED, PositionCalculator.PRECISION).divide(total, PositionCalculator.PRECISION);
            slices.add(new Slice(entry.getKey(), entry.getValue(), percent));
        }
        slices.sort(Comparator.<Slice, Boolean>comparing(slice -> slice.label().isEmpty())
                .thenComparing(Slice::value, Comparator.reverseOrder()));
        return slices;
    }
}
