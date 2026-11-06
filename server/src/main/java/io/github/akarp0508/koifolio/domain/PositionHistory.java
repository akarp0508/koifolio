package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** What a position was worth on each day there's a price for. */
public final class PositionHistory {

    private PositionHistory() {
    }

    /** @param traded a buy or sell happened since the previous point */
    public record Point(LocalDate date, BigDecimal quantity, BigDecimal close, BigDecimal value, BigDecimal cost,
                        boolean traded) {
    }

    /**
     * Starts at the first trade.
     *
     * @param trades oldest first
     * @param closes oldest first
     */
    public static List<Point> calculate(List<DatedTrade> trades, List<DailyClose> closes, CostMethod method) {
        List<Point> points = new ArrayList<>();
        List<Trade> applied = new ArrayList<>();
        PositionResult position = null;
        int next = 0;

        for (DailyClose close : closes) {
            boolean traded = false;
            while (next < trades.size() && !trades.get(next).date().isAfter(close.date())) {
                applied.add(trades.get(next).trade());
                next++;
                traded = true;
            }
            if (traded) {
                position = PositionCalculator.calculate(applied, method);
            }
            if (position != null) {
                BigDecimal value = position.quantity().multiply(close.close(), PositionCalculator.PRECISION);
                points.add(new Point(close.date(), position.quantity(), close.close(), value, position.cost(), traded));
            }
        }
        return points;
    }
}
