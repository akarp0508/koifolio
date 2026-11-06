package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompanyTotalTest {

    private static CompanyTotal.Line line(String value, String cost, String realized, String weight) {
        return new CompanyTotal.Line(Optional.ofNullable(value).map(BigDecimal::new), new BigDecimal(cost),
                new BigDecimal(realized), Optional.ofNullable(weight).map(BigDecimal::new));
    }

    @Test
    void addsUpAccounts() {
        CompanyTotal total = CompanyTotal.of(List.of(line("800", "600", "10", "16"), line("400", "400", "20", "8")));

        assertThat(total.value().orElseThrow()).isEqualByComparingTo("1200");
        assertThat(total.gain().orElseThrow()).isEqualByComparingTo("200");
        assertThat(total.gainPercent().orElseThrow()).isEqualByComparingTo("20");
        assertThat(total.realized()).isEqualByComparingTo("30");
        assertThat(total.weight().orElseThrow()).isEqualByComparingTo("24");
    }

    @Test
    void anUnpricedHoldingsCostStaysOutOfTheGain() {
        CompanyTotal total = CompanyTotal.of(List.of(line("150", "100", "0", "10"), line(null, "900", "5", null)));

        assertThat(total.gain().orElseThrow()).isEqualByComparingTo("50");
        assertThat(total.gainPercent().orElseThrow()).isEqualByComparingTo("50");
        assertThat(total.realized()).isEqualByComparingTo("5");
    }

    @Test
    void withoutAnyValueOnlyRealizedRemains() {
        CompanyTotal total = CompanyTotal.of(List.of(line(null, "100", "7", null)));

        assertThat(total.value()).isEmpty();
        assertThat(total.gain()).isEmpty();
        assertThat(total.weight()).isEmpty();
        assertThat(total.realized()).isEqualByComparingTo("7");
    }

    @Test
    void freeSharesHaveNoPercent() {
        assertThat(CompanyTotal.of(List.of(line("50", "0", "0", "1"))).gainPercent()).isEmpty();
    }
}
