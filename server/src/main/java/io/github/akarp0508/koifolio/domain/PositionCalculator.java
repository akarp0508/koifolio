package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Adds up one instrument's trades in one account. Average cost is FIFO with a single pooled lot,
 * so both methods share the same selling code.
 */
public final class PositionCalculator {

    static final MathContext PRECISION = MathContext.DECIMAL128;

    private PositionCalculator() {
    }

    private record Lot(BigDecimal quantity, BigDecimal cost) {

        Lot merge(Lot other) {
            return new Lot(quantity.add(other.quantity, PRECISION), cost.add(other.cost, PRECISION));
        }

        /** The part of this lot's cost that {@code part} of its quantity carries. */
        BigDecimal costOf(BigDecimal part) {
            return cost.multiply(part, PRECISION).divide(quantity, PRECISION);
        }
    }

    /** @param trades oldest first */
    public static PositionResult calculate(List<Trade> trades, CostMethod method) {
        Deque<Lot> lots = new ArrayDeque<>();
        BigDecimal realized = BigDecimal.ZERO;
        boolean incomplete = false;

        for (Trade trade : trades) {
            BigDecimal value = trade.quantity().multiply(trade.price(), PRECISION);
            if (trade.side() == Trade.Side.BUY) {
                Lot lot = new Lot(trade.quantity(), value.add(trade.fee(), PRECISION));
                lots.addLast(method == CostMethod.AVERAGE && !lots.isEmpty() ? lots.removeLast().merge(lot) : lot);
                continue;
            }

            BigDecimal covered = trade.quantity().min(held(lots));
            incomplete |= covered.compareTo(trade.quantity()) < 0;
            if (covered.signum() > 0) {
                BigDecimal proceeds = value.subtract(trade.fee(), PRECISION);
                BigDecimal coveredProceeds = proceeds.multiply(covered, PRECISION).divide(trade.quantity(), PRECISION);
                realized = realized.add(coveredProceeds.subtract(removeCost(lots, covered), PRECISION), PRECISION);
            }
        }

        BigDecimal cost = lots.stream().map(Lot::cost).reduce(BigDecimal.ZERO, (a, b) -> a.add(b, PRECISION));
        return new PositionResult(held(lots), cost, realized, incomplete);
    }

    private static BigDecimal held(Deque<Lot> lots) {
        return lots.stream().map(Lot::quantity).reduce(BigDecimal.ZERO, (a, b) -> a.add(b, PRECISION));
    }

    /** Takes {@code quantity} from the oldest lots and returns what it cost. */
    private static BigDecimal removeCost(Deque<Lot> lots, BigDecimal quantity) {
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal remaining = quantity;
        while (remaining.signum() > 0) {
            Lot lot = lots.removeFirst();
            if (remaining.compareTo(lot.quantity()) < 0) {
                BigDecimal partCost = lot.costOf(remaining);
                lots.addFirst(new Lot(lot.quantity().subtract(remaining, PRECISION), lot.cost().subtract(partCost, PRECISION)));
                return cost.add(partCost, PRECISION);
            }
            cost = cost.add(lot.cost(), PRECISION);
            remaining = remaining.subtract(lot.quantity(), PRECISION);
        }
        return cost;
    }
}
