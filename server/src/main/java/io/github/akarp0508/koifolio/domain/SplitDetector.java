package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compares closes already stored with what the source says now. The source adjusts past prices
 * for splits, so a split shows up as old closes changing, all by the same ratio.
 */
public final class SplitDetector {

    /** Smaller differences are corrections, not splits. */
    private static final double MIN_CHANGE = 0.05;
    private static final double RATIO_TOLERANCE = 0.01;
    private static final int MIN_CHANGED_DAYS = 2;
    private static final List<Integer> USUAL_OLD_SHARES = List.of(1, 2, 3, 4, 5, 10);

    private SplitDetector() {
    }

    public record DetectedSplit(int from, int to) {
    }

    /**
     * @param rewritten past closes changed, so stored history is out of date
     * @param split the change looks like a split, shown as {@code to} new shares for {@code from} old
     */
    public record Result(boolean rewritten, Optional<DetectedSplit> split) {
    }

    public static Result compare(List<DailyClose> stored, List<DailyClose> fetched) {
        Map<LocalDate, BigDecimal> now = new HashMap<>();
        fetched.forEach(close -> now.put(close.date(), close.close()));

        List<Double> ratios = new ArrayList<>();
        for (DailyClose close : stored) {
            BigDecimal current = now.get(close.date());
            if (current != null && current.signum() > 0) {
                double ratio = close.close().doubleValue() / current.doubleValue();
                if (Math.abs(ratio - 1) > MIN_CHANGE) {
                    ratios.add(ratio);
                }
            }
        }
        if (ratios.isEmpty()) {
            return new Result(false, Optional.empty());
        }

        double first = ratios.getFirst();
        boolean consistent = ratios.stream().allMatch(ratio -> Math.abs(ratio / first - 1) <= RATIO_TOLERANCE);
        Optional<DetectedSplit> split = ratios.size() >= MIN_CHANGED_DAYS && consistent ? asShares(first) : Optional.empty();
        return new Result(true, split);
    }

    /** The simplest usual form: 1.5 is 3 for 2. Any other ratio isn't a split anyone would announce. */
    private static Optional<DetectedSplit> asShares(double ratio) {
        if (ratio < 1) {
            long from = Math.round(1 / ratio);
            return isClose((double) 1 / from, ratio) ? Optional.of(new DetectedSplit((int) from, 1)) : Optional.empty();
        }
        for (int from : USUAL_OLD_SHARES) {
            long to = Math.round(ratio * from);
            if (isClose((double) to / from, ratio)) {
                return Optional.of(new DetectedSplit(from, (int) to));
            }
        }
        return Optional.empty();
    }

    private static boolean isClose(double candidate, double ratio) {
        return Math.abs(candidate / ratio - 1) <= RATIO_TOLERANCE;
    }
}
