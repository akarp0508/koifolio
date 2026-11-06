package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositionHistoryTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 2);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);
    private static final LocalDate THU = MON.plusDays(3);
    private static final LocalDate SAT = MON.plusDays(5);

    private static DatedTrade buy(LocalDate date, String quantity, String price) {
        return new DatedTrade(date, new Trade(Trade.Side.BUY, new BigDecimal(quantity), new BigDecimal(price), BigDecimal.ZERO));
    }

    private static DatedTrade sell(LocalDate date, String quantity, String price) {
        return new DatedTrade(date, new Trade(Trade.Side.SELL, new BigDecimal(quantity), new BigDecimal(price), BigDecimal.ZERO));
    }

    private static DailyClose close(LocalDate date, String price) {
        return new DailyClose(date, new BigDecimal(price));
    }

    private static List<PositionHistory.Point> history(List<DatedTrade> trades, DailyClose... closes) {
        return PositionHistory.calculate(trades, List.of(closes), CostMethod.AVERAGE);
    }

    @Test
    void valueIsQuantityTimesThatDaysClose() {
        List<PositionHistory.Point> points = history(List.of(buy(MON, "10", "100")), close(MON, "100"), close(TUE, "110"));

        assertThat(points).hasSize(2);
        assertThat(points.get(1).value()).isEqualByComparingTo("1100");
        assertThat(points.get(1).cost()).isEqualByComparingTo("1000");
    }

    @Test
    void startsAtTheFirstTrade() {
        List<PositionHistory.Point> points = history(List.of(buy(TUE, "1", "1")), close(MON, "5"), close(TUE, "6"));

        assertThat(points).extracting(PositionHistory.Point::date).containsExactly(TUE);
    }

    @Test
    void laterTradesChangeOnlyLaterDays() {
        List<PositionHistory.Point> points = history(List.of(buy(MON, "10", "100"), buy(WED, "5", "120")),
                close(MON, "100"), close(TUE, "100"), close(WED, "120"), close(THU, "120"));

        assertThat(points).extracting(point -> point.quantity().intValue()).containsExactly(10, 10, 15, 15);
        assertThat(points).extracting(PositionHistory.Point::traded).containsExactly(true, false, true, false);
        assertThat(points.get(3).cost()).isEqualByComparingTo("1600");
    }

    @Test
    void aSaleLowersTheCostByTheMethod() {
        List<DatedTrade> trades = List.of(buy(MON, "10", "100"), buy(TUE, "10", "120"), sell(WED, "5", "150"));
        DailyClose[] closes = {close(WED, "150")};

        assertThat(PositionHistory.calculate(trades, List.of(closes), CostMethod.AVERAGE).getLast().cost())
                .isEqualByComparingTo("1650");
        assertThat(PositionHistory.calculate(trades, List.of(closes), CostMethod.FIFO).getLast().cost())
                .isEqualByComparingTo("1700");
    }

    @Test
    void aTradeOnADayWithoutACloseShowsOnTheNextOne() {
        List<PositionHistory.Point> points = history(List.of(buy(SAT, "1", "10")),
                close(THU, "9"), close(SAT.plusDays(2), "11"));

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().traded()).isTrue();
        assertThat(points.getFirst().value()).isEqualByComparingTo("11");
    }

    @Test
    void soldOutDaysAreWorthNothing() {
        List<PositionHistory.Point> points = history(List.of(buy(MON, "1", "10"), sell(TUE, "1", "12")),
                close(MON, "10"), close(TUE, "12"), close(WED, "13"));

        assertThat(points.get(2).value()).isEqualByComparingTo("0");
        assertThat(points.get(2).cost()).isEqualByComparingTo("0");
    }

    @Test
    void noClosesMeansNoPoints() {
        assertThat(history(List.of(buy(MON, "1", "1")))).isEmpty();
    }
}
