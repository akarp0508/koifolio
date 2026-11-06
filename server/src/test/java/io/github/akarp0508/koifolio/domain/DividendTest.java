package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DividendTest {

    private static Dividend dividend(String gross, String withheld) {
        return new Dividend(LocalDate.of(2026, 6, 1), new BigDecimal(gross), new BigDecimal(withheld));
    }

    @Test
    void netIsWhatsLeftAfterWithholding() {
        assertThat(dividend("100", "15").net()).isEqualByComparingTo("85");
    }

    @Test
    void lessWithheldThanTheRateLeavesTheDifference() {
        assertThat(dividend("100", "15").taxLeft(new BigDecimal("19"))).isEqualByComparingTo("4");
    }

    @Test
    void enoughWithheldLeavesNothing() {
        assertThat(dividend("100", "19").taxLeft(new BigDecimal("19"))).isEqualByComparingTo("0");
    }

    @Test
    void moreWithheldThanTheRateIsntARefund() {
        assertThat(dividend("100", "30").taxLeft(new BigDecimal("19"))).isEqualByComparingTo("0");
    }

    @Test
    void aTaxFreeAccountOwesNothing() {
        assertThat(dividend("100", "0").taxLeft(BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void addsUpAnyAmount() {
        List<Dividend> dividends = List.of(dividend("100", "15"), dividend("50", "5"));

        assertThat(Dividend.total(dividends, Dividend::gross)).isEqualByComparingTo("150");
        assertThat(Dividend.total(dividends, Dividend::net)).isEqualByComparingTo("130");
        assertThat(Dividend.total(List.of(), Dividend::net)).isEqualByComparingTo("0");
    }
}
