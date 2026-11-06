package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeightsTest {

    @Test
    void cashCountsTowardsTheTotal() {
        Map<String, BigDecimal> weights = Weights.of(Map.of("A", new BigDecimal("30"), "B", new BigDecimal("50")), new BigDecimal("20"));

        assertThat(weights.get("A")).isEqualByComparingTo("30");
        assertThat(weights.get("B")).isEqualByComparingTo("50");
    }

    @Test
    void negativeCashCanTakeAWeightPastAHundred() {
        assertThat(Weights.of(Map.of("A", new BigDecimal("120")), new BigDecimal("-20")).get("A")).isEqualByComparingTo("120");
    }

    @Test
    void noPositiveTotalGivesNoWeights() {
        assertThat(Weights.of(Map.of("A", BigDecimal.ZERO), BigDecimal.ZERO)).isEmpty();
        assertThat(Weights.of(Map.of("A", new BigDecimal("10")), new BigDecimal("-20"))).isEmpty();
    }

    @Test
    void nothingGivesNothing() {
        assertThat(Weights.of(Map.<String, BigDecimal>of(), new BigDecimal("100"))).isEmpty();
    }
}
