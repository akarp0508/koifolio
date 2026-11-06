package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Whether holdings keep to a limit, in percent of their value plus cash, all in one currency.
 *
 * @param actual the biggest company's weight or the low-risk weight, empty when there's no positive total to check
 * @param breaches the companies over a maximum, biggest first
 */
public record RuleCheck(boolean passed, Optional<BigDecimal> actual, List<Breach> breaches) {

    /** @param risk empty when unknown, which never counts as low risk */
    public record Holding(String ticker, BigDecimal value, Optional<Integer> risk) {
    }

    public record Breach(String ticker, BigDecimal weight) {
    }

    /** A company held twice counts as one. */
    public static RuleCheck maxPositionWeight(BigDecimal limit, List<Holding> holdings, BigDecimal cash) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        holdings.forEach(holding -> values.merge(holding.ticker(), holding.value(),
                (a, b) -> a.add(b, PositionCalculator.PRECISION)));
        Map<String, BigDecimal> weights = Weights.of(values, cash);

        Optional<BigDecimal> biggest = weights.values().stream().max(Comparator.naturalOrder());
        List<Breach> breaches = weights.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(limit) > 0)
                .map(entry -> new Breach(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(Breach::weight).reversed())
                .toList();
        return new RuleCheck(breaches.isEmpty(), biggest, breaches);
    }

    /** Cash is as safe as it gets, so it always counts as low risk. */
    public static RuleCheck minLowRiskWeight(BigDecimal limit, int maxRisk, List<Holding> holdings, BigDecimal cash) {
        BigDecimal low = cash;
        BigDecimal rest = BigDecimal.ZERO;
        for (Holding holding : holdings) {
            if (holding.risk().filter(risk -> risk <= maxRisk).isPresent()) {
                low = low.add(holding.value(), PositionCalculator.PRECISION);
            } else {
                rest = rest.add(holding.value(), PositionCalculator.PRECISION);
            }
        }

        BigDecimal weight = Weights.of(Map.of("low", low, "rest", rest), BigDecimal.ZERO).get("low");
        if (weight == null) {
            return new RuleCheck(true, Optional.empty(), List.of());
        }
        return new RuleCheck(weight.compareTo(limit) >= 0, Optional.of(weight), List.of());
    }
}
