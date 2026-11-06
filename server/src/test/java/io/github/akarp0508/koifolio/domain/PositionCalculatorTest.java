package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositionCalculatorTest {

    private static final BigDecimal ALMOST = new BigDecimal("1e-28");

    private static Trade buy(String quantity, String price) {
        return buy(quantity, price, "0");
    }

    private static Trade buy(String quantity, String price, String fee) {
        return new Trade(Trade.Side.BUY, new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(fee));
    }

    private static Trade sell(String quantity, String price) {
        return sell(quantity, price, "0");
    }

    private static Trade sell(String quantity, String price, String fee) {
        return new Trade(Trade.Side.SELL, new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(fee));
    }

    private static PositionResult fifo(Trade... trades) {
        return PositionCalculator.calculate(List.of(trades), CostMethod.FIFO);
    }

    private static PositionResult average(Trade... trades) {
        return PositionCalculator.calculate(List.of(trades), CostMethod.AVERAGE);
    }

    private static Trade split(String from, String to) {
        return Trade.split(new BigDecimal(from), new BigDecimal(to));
    }

    @Test
    void aSplitChangesQuantityButNotCost() {
        PositionResult result = average(buy("10", "500"), split("1", "10"));

        assertThat(result.quantity()).isEqualByComparingTo("100");
        assertThat(result.cost()).isEqualByComparingTo("5000");
        assertThat(result.averageCost()).isEqualByComparingTo("50");
    }

    @Test
    void aReverseSplitStaysExact() {
        PositionResult result = average(buy("30", "1"), split("3", "1"));

        assertThat(result.quantity()).isEqualByComparingTo("10");
    }

    @Test
    void aOneForThreeSplitDoesNotLoseShares() {
        assertThat(average(buy("9", "1"), split("3", "1"), split("1", "3")).quantity()).isEqualByComparingTo("9");
    }

    @Test
    void sellingAfterASplitUsesTheNewQuantity() {
        PositionResult result = average(buy("10", "100"), split("1", "2"), sell("20", "60"));

        assertThat(result.isClosed()).isTrue();
        assertThat(result.incomplete()).isFalse();
        assertThat(result.realizedGain()).isEqualByComparingTo("200");
    }

    @Test
    void aSplitKeepsFifoLotsApart() {
        PositionResult result = fifo(buy("10", "100"), buy("10", "200"), split("1", "2"), sell("20", "100"));

        assertThat(result.cost()).isEqualByComparingTo("2000");
        assertThat(result.realizedGain()).isEqualByComparingTo("1000");
    }

    @Test
    void aSplitWithNothingHeldDoesNothing() {
        PositionResult result = fifo(split("1", "2"), buy("1", "10"));

        assertThat(result.quantity()).isEqualByComparingTo("1");
    }

    @Test
    void fifoSellsTheOldestSharesFirst() {
        PositionResult result = fifo(buy("10", "100"), buy("10", "120"), sell("5", "150"));

        assertThat(result.quantity()).isEqualByComparingTo("15");
        assertThat(result.cost()).isEqualByComparingTo("1700");
        assertThat(result.realizedGain()).isEqualByComparingTo("250");
    }

    @Test
    void averagePoolsTheCost() {
        PositionResult result = average(buy("10", "100"), buy("10", "120"), sell("5", "150"));

        assertThat(result.quantity()).isEqualByComparingTo("15");
        assertThat(result.averageCost()).isEqualByComparingTo("110");
        assertThat(result.cost()).isEqualByComparingTo("1650");
        assertThat(result.realizedGain()).isEqualByComparingTo("200");
    }

    @Test
    void bothMethodsAgreeOnceEverythingIsSold() {
        Trade[] trades = {buy("10", "100"), buy("10", "120"), sell("20", "150")};

        assertThat(fifo(trades).realizedGain()).isEqualByComparingTo("800");
        assertThat(average(trades).realizedGain()).isEqualByComparingTo("800");
    }

    @Test
    void fifoSaleCanSpanSeveralLots() {
        PositionResult result = fifo(buy("10", "100"), buy("10", "120"), sell("15", "150"));

        assertThat(result.cost()).isEqualByComparingTo("600");
        assertThat(result.realizedGain()).isEqualByComparingTo("650");
    }

    @Test
    void averageCarriesOnAfterASale() {
        PositionResult result = average(buy("10", "100"), sell("5", "100"), buy("5", "200"));

        assertThat(result.quantity()).isEqualByComparingTo("10");
        assertThat(result.averageCost()).isEqualByComparingTo("150");
    }

    @Test
    void buyFeeRaisesCostAndSellFeeLowersTheGain() {
        PositionResult result = fifo(buy("10", "100", "5"), sell("10", "110", "3"));

        assertThat(result.realizedGain()).isEqualByComparingTo("92");
    }

    @Test
    void feeIsPartOfTheAverageCost() {
        assertThat(average(buy("10", "100", "10")).averageCost()).isEqualByComparingTo("101");
    }

    @Test
    void lossesAreNegative() {
        assertThat(fifo(buy("10", "100"), sell("10", "80")).realizedGain()).isEqualByComparingTo("-200");
    }

    @Test
    void sellingEverythingClosesThePosition() {
        PositionResult result = fifo(buy("10", "100"), sell("10", "110"));

        assertThat(result.isClosed()).isTrue();
        assertThat(result.cost()).isEqualByComparingTo("0");
        assertThat(result.incomplete()).isFalse();
    }

    @Test
    void fractionalShares() {
        PositionResult result = average(buy("0.5", "100"), sell("0.125", "200"));

        assertThat(result.quantity()).isEqualByComparingTo("0.375");
        assertThat(result.realizedGain()).isEqualByComparingTo("12.5");
    }

    @Test
    void sellingAThirdKeepsTheTotalCost() {
        PositionResult result = average(buy("3", "100"), sell("1", "100"));

        assertThat(result.cost()).isCloseTo(new BigDecimal("200"), within(ALMOST));
        assertThat(result.realizedGain()).isCloseTo(BigDecimal.ZERO, within(ALMOST));
    }

    @Test
    void oversoldSaleCountsOnlyTheCoveredShares() {
        PositionResult result = fifo(buy("5", "100"), sell("10", "200", "10"));

        assertThat(result.quantity()).isEqualByComparingTo("0");
        assertThat(result.realizedGain()).isEqualByComparingTo("495");
        assertThat(result.incomplete()).isTrue();
    }

    @Test
    void saleWithNoKnownSharesGainsNothing() {
        PositionResult result = average(sell("10", "200"));

        assertThat(result.realizedGain()).isEqualByComparingTo("0");
        assertThat(result.incomplete()).isTrue();
    }

    @Test
    void laterBuyAfterAnOversoldSaleStartsFresh() {
        PositionResult result = fifo(sell("10", "200"), buy("5", "100"));

        assertThat(result.quantity()).isEqualByComparingTo("5");
        assertThat(result.cost()).isEqualByComparingTo("500");
        assertThat(result.incomplete()).isTrue();
    }

    @Test
    void freeSharesLowerTheAverage() {
        assertThat(average(buy("10", "100"), buy("10", "0")).averageCost()).isEqualByComparingTo("50");
    }
}
