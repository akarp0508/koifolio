package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * What a company adds up to across accounts, all in one currency.
 *
 * @param value empty when nothing has a value
 * @param gain value against the cost of the lines that have a value
 * @param gainPercent empty when those lines cost nothing
 * @param dividends net of tax withheld
 * @param weight the lines' weights added up, empty when none has one
 */
public record CompanyTotal(Optional<BigDecimal> value, Optional<BigDecimal> gain, Optional<BigDecimal> gainPercent,
                           BigDecimal realized, BigDecimal dividends, Optional<BigDecimal> weight) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** @param value empty for a closed or unpriced holding, so its cost doesn't count either */
    public record Line(Optional<BigDecimal> value, BigDecimal cost, BigDecimal realized, BigDecimal dividends,
                       Optional<BigDecimal> weight) {
    }

    public static CompanyTotal of(List<Line> lines) {
        List<Line> valued = lines.stream().filter(line -> line.value().isPresent()).toList();
        BigDecimal realized = sum(lines.stream().map(Line::realized).toList());
        BigDecimal dividends = sum(lines.stream().map(Line::dividends).toList());
        List<BigDecimal> weights = lines.stream().flatMap(line -> line.weight().stream()).toList();
        Optional<BigDecimal> weight = weights.isEmpty() ? Optional.empty() : Optional.of(sum(weights));
        if (valued.isEmpty()) {
            return new CompanyTotal(Optional.empty(), Optional.empty(), Optional.empty(), realized, dividends, weight);
        }

        BigDecimal value = sum(valued.stream().map(line -> line.value().orElseThrow()).toList());
        BigDecimal cost = sum(valued.stream().map(Line::cost).toList());
        BigDecimal gain = value.subtract(cost, PositionCalculator.PRECISION);
        Optional<BigDecimal> percent = cost.signum() == 0 ? Optional.empty()
                : Optional.of(gain.multiply(HUNDRED, PositionCalculator.PRECISION).divide(cost, PositionCalculator.PRECISION));
        return new CompanyTotal(Optional.of(value), Optional.of(gain), percent, realized, dividends, weight);
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, PositionCalculator.PRECISION));
    }
}
