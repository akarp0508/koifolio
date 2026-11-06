package io.github.akarp0508.koifolio.bond;

import java.util.Arrays;
import java.util.Optional;

/** Polish retail bonds that capitalise interest yearly. The rest pay interest out or run for months, so they aren't covered yet. */
enum BondKind {
    TOS(3, true),
    ROS(6, false),
    EDO(10, false),
    ROD(12, false);

    final int termYears;
    final boolean fixedRate;

    BondKind(int termYears, boolean fixedRate) {
        this.termYears = termYears;
        this.fixedRate = fixedRate;
    }

    static Optional<BondKind> ofSeries(String series) {
        return Arrays.stream(values()).filter(kind -> series.startsWith(kind.name())).findFirst();
    }
}
