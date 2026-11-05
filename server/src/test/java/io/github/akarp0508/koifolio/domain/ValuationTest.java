package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ValuationTest {

    private static PositionResult holding(String quantity, String cost) {
        return new PositionResult(new BigDecimal(quantity), new BigDecimal(cost), BigDecimal.ZERO, false);
    }

    @Test
    void gain() {
        Valuation valuation = Valuation.of(holding("15", "1650"), new BigDecimal("151"));

        assertThat(valuation.value()).isEqualByComparingTo("2265");
        assertThat(valuation.gain()).isEqualByComparingTo("615");
        assertThat(valuation.gainPercent().orElseThrow().doubleValue()).isCloseTo(37.27, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void loss() {
        Valuation valuation = Valuation.of(holding("5", "300"), new BigDecimal("58"));

        assertThat(valuation.gain()).isEqualByComparingTo("-10");
        assertThat(valuation.gainPercent().orElseThrow().signum()).isNegative();
    }

    @Test
    void freeSharesHaveAValueButNoPercentage() {
        Valuation valuation = Valuation.of(holding("10", "0"), new BigDecimal("5"));

        assertThat(valuation.gain()).isEqualByComparingTo("50");
        assertThat(valuation.gainPercent()).isEmpty();
    }
}
