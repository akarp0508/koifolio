package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanCheckTest {

    private static PlanCheck check(String price, String target, String stop) {
        return PlanCheck.of(new BigDecimal(price), Optional.ofNullable(target).map(BigDecimal::new),
                Optional.ofNullable(stop).map(BigDecimal::new));
    }

    @Test
    void measuresDistanceFromThePrice() {
        PlanCheck check = check("100", "180", "80");

        assertThat(check.targetDistance().orElseThrow()).isEqualByComparingTo("80");
        assertThat(check.stopDistance().orElseThrow()).isEqualByComparingTo("-20");
        assertThat(check.signal()).isEmpty();
    }

    @Test
    void nearTarget() {
        assertThat(check("172", "180", null).signal()).contains(PlanCheck.Signal.NEAR_TARGET);
    }

    @Test
    void nearStop() {
        assertThat(check("125", null, "120").signal()).contains(PlanCheck.Signal.NEAR_STOP);
    }

    @Test
    void exactlyFivePercentCounts() {
        assertThat(check("100", "105", null).signal()).contains(PlanCheck.Signal.NEAR_TARGET);
    }

    @Test
    void justOutsideDoesNot() {
        assertThat(check("100", "105.01", null).signal()).isEmpty();
    }

    @Test
    void reachedAndPast() {
        assertThat(check("180", "180", null).signal()).contains(PlanCheck.Signal.TARGET_REACHED);
        assertThat(check("200", "180", null).signal()).contains(PlanCheck.Signal.TARGET_REACHED);
        assertThat(check("120", null, "120").signal()).contains(PlanCheck.Signal.STOP_HIT);
        assertThat(check("100", null, "120").signal()).contains(PlanCheck.Signal.STOP_HIT);
    }

    @Test
    void stopWinsWhenLevelsAreClose() {
        assertThat(check("100", "103", "98").signal()).contains(PlanCheck.Signal.NEAR_STOP);
    }

    @Test
    void noLevelsNoSignal() {
        PlanCheck check = check("100", null, null);

        assertThat(check.targetDistance()).isEmpty();
        assertThat(check.signal()).isEmpty();
    }
}
