package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuleCheckTest {

    private static final BigDecimal TWENTY = new BigDecimal("20");
    private static final BigDecimal FIFTY = new BigDecimal("50");

    private static RuleCheck.Holding holding(String ticker, String value, Integer risk) {
        return new RuleCheck.Holding(ticker, new BigDecimal(value), Optional.ofNullable(risk));
    }

    @Test
    void underTheMaximumPasses() {
        RuleCheck check = RuleCheck.maxPositionWeight(TWENTY, List.of(holding("A", "10", 1), holding("B", "20", 1)), new BigDecimal("70"));

        assertThat(check.passed()).isTrue();
        assertThat(check.actual().orElseThrow()).isEqualByComparingTo("20");
        assertThat(check.breaches()).isEmpty();
    }

    @Test
    void overTheMaximumNamesEachCompanyBiggestFirst() {
        RuleCheck check = RuleCheck.maxPositionWeight(TWENTY,
                List.of(holding("A", "30", 1), holding("B", "60", 1), holding("C", "10", 1)), BigDecimal.ZERO);

        assertThat(check.passed()).isFalse();
        assertThat(check.breaches()).extracting(RuleCheck.Breach::ticker).containsExactly("B", "A");
        assertThat(check.breaches().getFirst().weight()).isEqualByComparingTo("60");
    }

    @Test
    void aCompanyInTwoAccountsCountsAsOne() {
        RuleCheck check = RuleCheck.maxPositionWeight(TWENTY, List.of(holding("A", "15", 1), holding("A", "15", 1)), new BigDecimal("70"));

        assertThat(check.passed()).isFalse();
        assertThat(check.actual().orElseThrow()).isEqualByComparingTo("30");
    }

    @Test
    void cashCountsAsLowRisk() {
        RuleCheck check = RuleCheck.minLowRiskWeight(FIFTY, 2, List.of(holding("A", "10", 2), holding("B", "50", 5)), new BigDecimal("40"));

        assertThat(check.passed()).isTrue();
        assertThat(check.actual().orElseThrow()).isEqualByComparingTo("50");
    }

    @Test
    void tooLittleLowRiskFails() {
        assertThat(RuleCheck.minLowRiskWeight(FIFTY, 2, List.of(holding("A", "60", 3)), new BigDecimal("40")).passed()).isFalse();
    }

    @Test
    void unknownRiskIsNotLowRisk() {
        RuleCheck check = RuleCheck.minLowRiskWeight(FIFTY, 5, List.of(holding("A", "100", null)), BigDecimal.ZERO);

        assertThat(check.passed()).isFalse();
        assertThat(check.actual().orElseThrow()).isEqualByComparingTo("0");
    }

    @Test
    void nothingToCheckPassesWithoutANumber() {
        assertThat(RuleCheck.maxPositionWeight(TWENTY, List.of(), BigDecimal.ZERO).actual()).isEmpty();
        RuleCheck check = RuleCheck.minLowRiskWeight(FIFTY, 2, List.of(), BigDecimal.ZERO);
        assertThat(check.passed()).isTrue();
        assertThat(check.actual()).isEmpty();
    }
}
