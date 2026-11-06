package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BondValueTest {

    private static final LocalDate BOUGHT = LocalDate.of(2025, 8, 14);
    private static final List<BigDecimal> FIXED = List.of(new BigDecimal("5.40"));

    private static BigDecimal tos(LocalDate day) {
        return BondValue.perBond(BOUGHT, 3, FIXED, day);
    }

    /** Every published value, across leap years and three years of capitalising. */
    @Test
    void matchesTheOfficialTablesOnEveryDay() throws Exception {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/bond-interest-tables.csv"), StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] cell = line.split(",");
                List<BigDecimal> rates = Arrays.stream(cell[2].split(" ")).map(BigDecimal::new).toList();
                BigDecimal value = BondValue.perBond(LocalDate.parse(cell[1]), 10, rates, LocalDate.parse(cell[3]));
                checked++;
                if (value.subtract(BondValue.NOMINAL).compareTo(new BigDecimal(cell[4])) != 0) {
                    wrong.add(line + " got " + value);
                }
            }
        }
        assertThat(checked).isEqualTo(1461);
        assertThat(wrong).isEmpty();
    }

    @Test
    void startsAtNominal() {
        assertThat(tos(BOUGHT)).isEqualByComparingTo("100");
        assertThat(tos(BOUGHT.minusDays(1))).isEqualByComparingTo("100");
    }

    @Test
    void capitalisesEachYear() {
        assertThat(tos(BOUGHT.plusYears(1))).isEqualByComparingTo("105.40");
        assertThat(tos(BOUGHT.plusYears(2))).isEqualByComparingTo("111.09");
    }

    @Test
    void stopsAtMaturity() {
        assertThat(tos(BOUGHT.plusYears(3))).isEqualByComparingTo("117.09");
        assertThat(tos(BOUGHT.plusYears(9))).isEqualByComparingTo("117.09");
    }

    @Test
    void theLastRateStandsInForLaterYears() {
        assertThat(BondValue.perBond(BOUGHT, 10, List.of(new BigDecimal("6"), new BigDecimal("4")), BOUGHT.plusYears(3)))
                .isEqualByComparingTo("114.65");
    }

    @Test
    void theFeeComesOffTheInterest() {
        assertThat(BondValue.afterEarlyFee(new BigDecimal("105.40"), new BigDecimal("1.00"))).isEqualByComparingTo("104.40");
    }

    @Test
    void theFeeNeverTakesMoreThanTheInterest() {
        assertThat(BondValue.afterEarlyFee(new BigDecimal("100.40"), new BigDecimal("1.00"))).isEqualByComparingTo("100");
        assertThat(BondValue.afterEarlyFee(new BigDecimal("100"), new BigDecimal("1.00"))).isEqualByComparingTo("100");
    }
}
