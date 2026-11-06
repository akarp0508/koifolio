package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.akarp0508.koifolio.domain.PortfolioHistory.Change;
import io.github.akarp0508.koifolio.domain.PortfolioHistory.Period;
import io.github.akarp0508.koifolio.domain.PortfolioHistory.Point;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioHistoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private static Point point(LocalDate date, String value, String cost) {
        return new Point(date, new BigDecimal(value), new BigDecimal(cost), BigDecimal.ZERO);
    }

    private static Change change(List<Point> points, Period period) {
        return PortfolioHistory.changes(points, TODAY).stream()
                .filter(found -> found.period() == period).findFirst().orElseThrow();
    }

    @Test
    void addsPositionsDayByDay() {
        List<Point> totals = PortfolioHistory.total(List.of(
                List.of(point(TODAY, "100", "50")), List.of(point(TODAY, "20", "10"))));

        assertThat(totals).hasSize(1);
        assertThat(totals.getFirst().value()).isEqualByComparingTo("120");
        assertThat(totals.getFirst().cost()).isEqualByComparingTo("60");
    }

    @Test
    void aMarketHolidayKeepsTheLastValue() {
        List<Point> polish = List.of(point(TODAY.minusDays(2), "100", "0"), point(TODAY, "110", "0"));
        List<Point> american = List.of(point(TODAY.minusDays(2), "50", "0"), point(TODAY.minusDays(1), "60", "0"),
                point(TODAY, "70", "0"));

        List<Point> totals = PortfolioHistory.total(List.of(polish, american));

        assertThat(totals).extracting(point -> point.value().intValue()).containsExactly(150, 160, 180);
    }

    @Test
    void aPositionCountsFromItsFirstPoint() {
        List<Point> totals = PortfolioHistory.total(List.of(
                List.of(point(TODAY.minusDays(1), "100", "0"), point(TODAY, "100", "0")),
                List.of(point(TODAY, "5", "0"))));

        assertThat(totals).extracting(point -> point.value().intValue()).containsExactly(100, 105);
    }

    @Test
    void aBuyIsNotProfit() {
        List<Point> points = List.of(point(TODAY.minusDays(1), "1000", "1000"), point(TODAY, "11000", "11000"));

        assertThat(change(points, Period.DAY).change()).isEqualByComparingTo("0");
    }

    @Test
    void aPriceMoveIs() {
        List<Point> points = List.of(point(TODAY.minusDays(1), "1000", "800"), point(TODAY, "1100", "800"));

        Change day = change(points, Period.DAY);

        assertThat(day.change()).isEqualByComparingTo("100");
        assertThat(day.percent().orElseThrow()).isEqualByComparingTo("10");
    }

    @Test
    void aSaleKeepsTheGainThroughRealized() {
        List<Point> points = List.of(point(TODAY.minusDays(1), "1000", "800"),
                new Point(TODAY, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200")));

        assertThat(change(points, Period.DAY).change()).isEqualByComparingTo("0");
    }

    @Test
    void comparesWithTheLastPointBeforeThePeriod() {
        LocalDate friday = LocalDate.of(2026, 11, 6);
        List<Point> points = List.of(point(friday.minusWeeks(1).minusDays(1), "90", "0"),
                point(friday.minusDays(1), "100", "0"), point(friday, "130", "0"));

        assertThat(change(points, Period.WEEK).change()).isEqualByComparingTo("40");
    }

    @Test
    void yearToDateStartsAtTheLastDayOfLastYear() {
        List<Point> points = List.of(point(LocalDate.of(2025, 12, 31), "100", "0"),
                point(LocalDate.of(2026, 1, 2), "150", "0"), point(TODAY, "200", "0"));

        assertThat(change(points, Period.YEAR_TO_DATE).change()).isEqualByComparingTo("100");
    }

    @Test
    void periodsBeyondTheHistoryAreLeftOut() {
        List<Point> points = List.of(point(TODAY.minusDays(10), "100", "0"), point(TODAY, "110", "0"));

        assertThat(PortfolioHistory.changes(points, TODAY)).extracting(Change::period)
                .containsExactly(Period.DAY, Period.WEEK);
    }

    @Test
    void nothingHeldAtTheStartHasNoPercentage() {
        List<Point> points = List.of(point(TODAY.minusDays(1), "0", "0"), point(TODAY, "100", "50"));

        assertThat(change(points, Period.DAY).percent()).isEmpty();
    }

    @Test
    void noPointsNoChanges() {
        assertThat(PortfolioHistory.changes(List.of(), TODAY)).isEmpty();
    }

    @Test
    void dividendsCountFromTheDayTheyWerePaid() {
        LocalDate before = TODAY.minusDays(2);
        LocalDate paid = TODAY.minusDays(1);
        List<Point> points = PortfolioHistory.withDividends(List.of(point(before, "100", "100"), point(paid, "98", "100")),
                List.of(new Dividend(paid, new BigDecimal("3"), new BigDecimal("1"))));

        assertThat(points.get(0).gain()).isEqualByComparingTo("0");
        assertThat(points.get(1).gain()).isEqualByComparingTo("0");
    }

    @Test
    void cashAddsToValueButNotToTheGain() {
        Point point = new Point(TODAY, new BigDecimal("110"), new BigDecimal("100"), new BigDecimal("5"));

        Point withCash = PortfolioHistory.withCash(List.of(point), day -> new BigDecimal("50")).getFirst();

        assertThat(withCash.value()).isEqualByComparingTo("160");
        assertThat(withCash.cost()).isEqualByComparingTo("150");
        assertThat(withCash.gain()).isEqualByComparingTo(point.gain());
    }
}
