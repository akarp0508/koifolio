package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Adds positions up over time. Markets close on different days, so a position keeps its last known
 * point until it has a new one. All amounts are in one currency.
 */
public final class PortfolioHistory {

    private PortfolioHistory() {
    }

    public record Point(LocalDate date, BigDecimal value, BigDecimal cost, BigDecimal realized) {

        /** Everything earned so far: deposits and buys don't change it. */
        public BigDecimal gain() {
            return value.subtract(cost, PositionCalculator.PRECISION).add(realized, PositionCalculator.PRECISION);
        }
    }

    public enum Period {
        DAY,
        WEEK,
        MONTH,
        YEAR_TO_DATE,
        YEAR;

        /** The last day before the period starts: the point to compare with. */
        LocalDate startingPoint(LocalDate today) {
            return switch (this) {
                case DAY -> today.minusDays(1);
                case WEEK -> today.minusWeeks(1);
                case MONTH -> today.minusMonths(1);
                case YEAR_TO_DATE -> today.withDayOfYear(1).minusDays(1);
                case YEAR -> today.minusYears(1);
            };
        }
    }

    /** @param percent of the value at the start, empty if nothing was held then */
    public record Change(Period period, BigDecimal change, Optional<BigDecimal> percent) {
    }

    /** @param series each position's points, oldest first */
    public static List<Point> total(List<List<Point>> series) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        List<NavigableMap<LocalDate, Point>> byDate = new ArrayList<>();
        for (List<Point> points : series) {
            NavigableMap<LocalDate, Point> map = new TreeMap<>();
            for (Point point : points) {
                map.put(point.date(), point);
                dates.add(point.date());
            }
            byDate.add(map);
        }

        List<Point> totals = new ArrayList<>();
        for (LocalDate date : dates) {
            BigDecimal value = BigDecimal.ZERO;
            BigDecimal cost = BigDecimal.ZERO;
            BigDecimal realized = BigDecimal.ZERO;
            for (NavigableMap<LocalDate, Point> points : byDate) {
                Map.Entry<LocalDate, Point> known = points.floorEntry(date);
                if (known != null) {
                    value = value.add(known.getValue().value(), PositionCalculator.PRECISION);
                    cost = cost.add(known.getValue().cost(), PositionCalculator.PRECISION);
                    realized = realized.add(known.getValue().realized(), PositionCalculator.PRECISION);
                }
            }
            totals.add(new Point(date, value, cost, realized));
        }
        return totals;
    }

    /** Adds dividends to what was realized by each day, net of tax withheld. Prices fall by what's paid out, so the gain would dip otherwise. */
    public static List<Point> withDividends(List<Point> points, List<Dividend> dividends) {
        return points.stream()
                .map(point -> new Point(point.date(), point.value(), point.cost(), point.realized().add(
                        Dividend.total(dividends.stream().filter(paid -> !paid.date().isAfter(point.date())).toList(), Dividend::net),
                        PositionCalculator.PRECISION)))
                .toList();
    }

    /** Periods reaching further back than the history are left out. */
    public static List<Change> changes(List<Point> points, LocalDate today) {
        List<Change> changes = new ArrayList<>();
        if (points.isEmpty()) {
            return changes;
        }
        NavigableMap<LocalDate, Point> byDate = new TreeMap<>();
        points.forEach(point -> byDate.put(point.date(), point));
        Point latest = points.getLast();

        for (Period period : Period.values()) {
            Map.Entry<LocalDate, Point> start = byDate.floorEntry(period.startingPoint(today));
            if (start != null) {
                BigDecimal change = latest.gain().subtract(start.getValue().gain(), PositionCalculator.PRECISION);
                BigDecimal base = start.getValue().value();
                Optional<BigDecimal> percent = base.signum() == 0 ? Optional.empty()
                        : Optional.of(change.multiply(new BigDecimal("100"), PositionCalculator.PRECISION)
                                .divide(base, PositionCalculator.PRECISION));
                changes.add(new Change(period, change, percent));
            }
        }
        return changes;
    }
}
