package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * What a position was worth on each day there's a price for. Closes are adjusted for splits, so a
 * close from before a split is scaled back up to what the shares really traded at.
 */
public final class PositionHistory {

    private PositionHistory() {
    }

    /** @param traded a buy or sell happened since the previous point */
    public record Point(LocalDate date, BigDecimal quantity, BigDecimal close, BigDecimal value, BigDecimal cost,
                        BigDecimal realized, boolean traded) {
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

        for (DailyClose daily : closes) {
            DailyClose close = new DailyClose(daily.date(), beforeLaterSplits(daily, trades));
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
                points.add(new Point(close.date(), position.quantity(), close.close(), value, position.cost(),
                        position.realizedGain(), traded));
            }
        }
        return points;
    }

    /**
     * Closes with the holding's own trade prices filling the days before they start, for a source that
     * doesn't cover it or hasn't delivered yet. Trade prices aren't split-adjusted like closes are, so a
     * holding with a split keeps its closes alone.
     *
     * @param trades oldest first, with prices in the same currency as the closes
     * @param closes oldest first
     */
    public static List<DailyClose> withTradePrices(List<DatedTrade> trades, List<DailyClose> closes) {
        if (trades.stream().anyMatch(trade -> trade.trade().side() == Trade.Side.SPLIT)) {
            return closes;
        }
        TreeMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        LocalDate firstClose = closes.isEmpty() ? LocalDate.MAX : closes.getFirst().date();
        trades.stream().filter(trade -> trade.date().isBefore(firstClose) && trade.trade().hasMoney())
                .forEach(trade -> prices.put(trade.date(), trade.trade().price()));
        List<DailyClose> filled = new ArrayList<>();
        prices.forEach((date, price) -> filled.add(new DailyClose(date, price)));
        filled.addAll(closes);
        return filled;
    }

    private static BigDecimal beforeLaterSplits(DailyClose close, List<DatedTrade> trades) {
        BigDecimal price = close.close();
        for (DatedTrade trade : trades) {
            if (trade.trade().side() == Trade.Side.SPLIT && trade.date().isAfter(close.date())) {
                price = trade.trade().applySplit(price);
            }
        }
        return price;
    }
}
