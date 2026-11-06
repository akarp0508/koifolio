package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.akarp0508.koifolio.domain.TaxEstimate.AccountGain;
import io.github.akarp0508.koifolio.domain.TaxEstimate.Group;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaxEstimateTest {

    private static AccountGain<String> account(String name, String rate, String realized, String unrealized) {
        return new AccountGain<>(name, new BigDecimal(rate), new BigDecimal(realized), new BigDecimal(unrealized));
    }

    private static DatedTrade buy(int year, String quantity, String price) {
        return new DatedTrade(LocalDate.of(year, 1, 2), new Trade(Trade.Side.BUY, new BigDecimal(quantity), new BigDecimal(price), BigDecimal.ZERO));
    }

    private static DatedTrade sell(int year, String quantity, String price) {
        return new DatedTrade(LocalDate.of(year, 6, 2), new Trade(Trade.Side.SELL, new BigDecimal(quantity), new BigDecimal(price), BigDecimal.ZERO));
    }

    @Test
    void lossesInOneAccountOffsetGainsInAnotherAtTheSameRate() {
        List<Group<String>> groups = TaxEstimate.group(List.of(
                account("Brokerage", "19", "4000", "0"), account("XTB", "19.00", "-1000", "0")));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().realized()).isEqualByComparingTo("3000");
        assertThat(groups.getFirst().tax()).isEqualByComparingTo("570");
        assertThat(groups.getFirst().accounts()).hasSize(2);
    }

    @Test
    void differentRatesStayApart() {
        List<Group<String>> groups = TaxEstimate.group(List.of(
                account("IKE", "0", "2500", "0"), account("Brokerage", "19", "-1000", "0")));

        assertThat(groups).extracting(group -> group.ratePercent().intValue()).containsExactly(19, 0);
        assertThat(groups).extracting(Group::tax).allSatisfy(tax -> assertThat(tax).isEqualByComparingTo("0"));
    }

    @Test
    void aNetLossOwesNothing() {
        assertThat(TaxEstimate.group(List.of(account("A", "19", "-500", "0"))).getFirst().tax()).isEqualByComparingTo("0");
    }

    @Test
    void ifSoldTodayIsTheExtraTax() {
        Group<String> group = TaxEstimate.group(List.of(account("A", "19", "1000", "2000"))).getFirst();

        assertThat(group.tax()).isEqualByComparingTo("190");
        assertThat(group.ifSoldToday()).isEqualByComparingTo("380");
    }

    @Test
    void thisYearsLossesReduceTheTaxIfSoldToday() {
        Group<String> group = TaxEstimate.group(List.of(account("A", "19", "-1000", "3000"))).getFirst();

        assertThat(group.tax()).isEqualByComparingTo("0");
        assertThat(group.ifSoldToday()).isEqualByComparingTo("380");
    }

    @Test
    void paperLossesReduceTheTaxAlreadyDue() {
        Group<String> group = TaxEstimate.group(List.of(account("A", "19", "1000", "-1500"))).getFirst();

        assertThat(group.ifSoldToday()).isEqualByComparingTo("-190");
    }

    @Test
    void gainsLandInTheYearOfTheSale() {
        Map<Integer, BigDecimal> years = TaxEstimate.gainsByYear(
                List.of(buy(2024, "10", "100"), sell(2025, "5", "120"), sell(2026, "5", "80")), CostMethod.AVERAGE);

        assertThat(years.get(2025)).isEqualByComparingTo("100");
        assertThat(years.get(2026)).isEqualByComparingTo("-100");
        assertThat(years).doesNotContainKey(2024);
    }

    @Test
    void severalSalesInAYearAddUp() {
        Map<Integer, BigDecimal> years = TaxEstimate.gainsByYear(
                List.of(buy(2024, "10", "100"), sell(2025, "5", "120"), sell(2025, "5", "130")), CostMethod.FIFO);

        assertThat(years.get(2025)).isEqualByComparingTo("250");
    }

    @Test
    void anOversoldSaleCountsOnlyKnownShares() {
        Map<Integer, BigDecimal> years = TaxEstimate.gainsByYear(List.of(buy(2024, "5", "100"), sell(2025, "10", "200")), CostMethod.FIFO);

        assertThat(years.get(2025)).isEqualByComparingTo("500");
    }

    private static TaxEstimate.AccountDividends<String> paid(String name, String rate, String gross, String withheld) {
        return new TaxEstimate.AccountDividends<>(name, new BigDecimal(rate),
                List.of(new Dividend(LocalDate.of(2026, 6, 1), new BigDecimal(gross), new BigDecimal(withheld))));
    }

    @Test
    void dividendsAddUpPerRateHighestFirst() {
        var groups = TaxEstimate.groupDividends(List.of(paid("IKE", "0", "50", "0"), paid("A", "19", "100", "15"),
                paid("B", "19.0", "200", "38")));

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).ratePercent()).isEqualByComparingTo("19");
        assertThat(groups.get(0).gross()).isEqualByComparingTo("300");
        assertThat(groups.get(0).withheld()).isEqualByComparingTo("53");
        assertThat(groups.get(0).taxLeft()).isEqualByComparingTo("4");
        assertThat(groups.get(1).taxLeft()).isEqualByComparingTo("0");
    }

    @Test
    void tooMuchWithheldOnOneDividendDoesntCoverAnother() {
        var account = new TaxEstimate.AccountDividends<>("A", new BigDecimal("19"), List.of(
                new Dividend(LocalDate.of(2026, 6, 1), new BigDecimal("100"), new BigDecimal("30")),
                new Dividend(LocalDate.of(2026, 7, 1), new BigDecimal("100"), new BigDecimal("15"))));

        assertThat(account.taxLeft()).isEqualByComparingTo("4");
    }
}
