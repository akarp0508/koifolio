package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.akarp0508.koifolio.domain.SplitDetector.DetectedSplit;
import io.github.akarp0508.koifolio.domain.SplitDetector.Result;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SplitDetectorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 2);

    private static List<DailyClose> closes(String... prices) {
        List<DailyClose> closes = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            closes.add(new DailyClose(DAY.plusDays(i), new BigDecimal(prices[i])));
        }
        return closes;
    }

    private static Result compare(List<DailyClose> stored, List<DailyClose> fetched) {
        return SplitDetector.compare(stored, fetched);
    }

    @Test
    void unchangedClosesAreNothing() {
        assertThat(compare(closes("100", "101"), closes("100", "101", "102"))).isEqualTo(new Result(false, java.util.Optional.empty()));
    }

    @Test
    void tenForOne() {
        assertThat(compare(closes("500", "510", "520"), closes("50", "51", "52", "53")).split())
                .contains(new DetectedSplit(1, 10));
    }

    @Test
    void reverseSplit() {
        assertThat(compare(closes("1", "2"), closes("10", "20")).split()).contains(new DetectedSplit(10, 1));
    }

    @Test
    void threeForTwo() {
        assertThat(compare(closes("150", "300"), closes("100", "200")).split()).contains(new DetectedSplit(2, 3));
    }

    @Test
    void realWorldRoundingStillMatches() {
        // Closes rounded to 2 places after dividing by 10
        assertThat(compare(closes("502.5", "497.3"), closes("50.25", "49.73")).split()).contains(new DetectedSplit(1, 10));
    }

    @Test
    void aSmallCorrectionIsNotASplitOrARewrite() {
        assertThat(compare(closes("100", "101"), closes("100.5", "101.5")).rewritten()).isFalse();
    }

    @Test
    void oneChangedDayIsARewriteButNotASplit() {
        Result result = compare(closes("100", "101"), closes("50", "101"));

        assertThat(result.rewritten()).isTrue();
        assertThat(result.split()).isEmpty();
    }

    @Test
    void inconsistentChangesAreNotASplit() {
        Result result = compare(closes("100", "100", "100"), closes("50", "25", "70"));

        assertThat(result.rewritten()).isTrue();
        assertThat(result.split()).isEmpty();
    }

    @Test
    void daysOnlyOnOneSideAreIgnored() {
        assertThat(compare(closes("100"), closes("100", "5")).rewritten()).isFalse();
    }

    @Test
    void aRatioNoCompanyWouldAnnounceIsNotASplit() {
        Result result = compare(closes("137", "274"), closes("100", "200"));

        assertThat(result.rewritten()).isTrue();
        assertThat(result.split()).isEmpty();
    }
}
