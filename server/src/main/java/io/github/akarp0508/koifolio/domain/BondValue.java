package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * What one Polish retail bond that capitalises yearly is worth on a day. Interest accrues on actual days
 * within each year from the purchase date, and each finished year joins the base.
 *
 * <p>The base isn't rounded between years, unlike what older issue letters say: only that matches the
 * Ministry of Finance's published tables, which the tests check day by day.
 */
public final class BondValue {

    public static final BigDecimal NOMINAL = new BigDecimal("100");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private BondValue() {
    }

    /** @param ratesPercent one per year, with the last one standing in for years without a rate */
    public static BigDecimal perBond(LocalDate purchase, int termYears, List<BigDecimal> ratesPercent, LocalDate day) {
        LocalDate maturity = purchase.plusYears(termYears);
        LocalDate end = day.isAfter(maturity) ? maturity : day;
        if (end.isBefore(purchase)) {
            return NOMINAL;
        }
        int year = Math.min((int) ChronoUnit.YEARS.between(purchase, end), termYears - 1);
        BigDecimal base = NOMINAL;
        for (int i = 0; i < year; i++) {
            base = base.multiply(BigDecimal.ONE.add(rate(ratesPercent, i), PositionCalculator.PRECISION), PositionCalculator.PRECISION);
        }
        LocalDate start = purchase.plusYears(year);
        BigDecimal elapsed = BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end));
        BigDecimal length = BigDecimal.valueOf(ChronoUnit.DAYS.between(start, purchase.plusYears(year + 1L)));
        BigDecimal growth = rate(ratesPercent, year).multiply(elapsed, PositionCalculator.PRECISION).divide(length, PositionCalculator.PRECISION);
        return base.multiply(BigDecimal.ONE.add(growth, PositionCalculator.PRECISION), PositionCalculator.PRECISION)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** The early redemption fee can't take back more than the interest earned. */
    public static BigDecimal afterEarlyFee(BigDecimal value, BigDecimal fee) {
        return value.subtract(fee.min(value.subtract(NOMINAL)));
    }

    private static BigDecimal rate(List<BigDecimal> ratesPercent, int year) {
        return ratesPercent.get(Math.min(year, ratesPercent.size() - 1)).divide(HUNDRED, PositionCalculator.PRECISION);
    }
}
