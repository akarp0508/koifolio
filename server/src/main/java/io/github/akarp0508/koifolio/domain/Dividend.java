package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * One payout, with both amounts in one currency.
 *
 * @param withheld tax taken at source
 */
public record Dividend(LocalDate date, BigDecimal gross, BigDecimal withheld) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public BigDecimal net() {
        return gross.subtract(withheld, PositionCalculator.PRECISION);
    }

    /** What's still owed at this rate. Too much withheld on one payout doesn't cover another, so it stops at zero. */
    public BigDecimal taxLeft(BigDecimal ratePercent) {
        BigDecimal due = gross.multiply(ratePercent, PositionCalculator.PRECISION).divide(HUNDRED, PositionCalculator.PRECISION);
        return due.subtract(withheld, PositionCalculator.PRECISION).max(BigDecimal.ZERO);
    }

    public static BigDecimal total(List<Dividend> dividends, Function<Dividend, BigDecimal> amount) {
        return dividends.stream().map(amount).reduce(BigDecimal.ZERO, (a, b) -> a.add(b, PositionCalculator.PRECISION));
    }
}
