package io.github.akarp0508.koifolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BreakdownTest {

    private static Breakdown.Part part(String label, String value) {
        return new Breakdown.Part(Optional.ofNullable(label), new BigDecimal(value));
    }

    @Test
    void groupsByLabelBiggestFirstWithPercentages() {
        List<Breakdown.Slice> slices = Breakdown.of(List.of(part("IT", "20"), part("Banks", "30"), part("IT", "10"), part("Energy", "40")));

        assertThat(slices).extracting(slice -> slice.label().orElseThrow()).containsExactly("Energy", "IT", "Banks");
        assertThat(slices.get(1).value()).isEqualByComparingTo("30");
        assertThat(slices.get(0).percent()).isEqualByComparingTo("40");
    }

    @Test
    void noLabelComesLastEvenIfBiggest() {
        List<Breakdown.Slice> slices = Breakdown.of(List.of(part(null, "90"), part("IT", "10")));

        assertThat(slices.getLast().label()).isEmpty();
        assertThat(slices.getLast().percent()).isEqualByComparingTo("90");
    }

    @Test
    void nothingGivesNothing() {
        assertThat(Breakdown.of(List.of())).isEmpty();
    }

    @Test
    void zeroTotalHasNoDivisionProblem() {
        assertThat(Breakdown.of(List.of(part("IT", "0"))).getFirst().percent()).isEqualByComparingTo("0");
    }
}
