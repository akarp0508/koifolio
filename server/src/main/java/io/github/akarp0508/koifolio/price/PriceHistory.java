package io.github.akarp0508.koifolio.price;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** How far a symbol's stored closes go, and when the source was last asked. */
@Entity
@Table(name = "price_history")
class PriceHistory {

    @Id
    private String symbol;

    @Column(name = "covered_from", nullable = false)
    private LocalDate coveredFrom;

    @Column(name = "checked_on", nullable = false)
    private LocalDate checkedOn;

    protected PriceHistory() {
    }

    PriceHistory(String symbol, LocalDate coveredFrom, LocalDate checkedOn) {
        this.symbol = symbol;
        this.coveredFrom = coveredFrom;
        this.checkedOn = checkedOn;
    }

    String getSymbol() {
        return symbol;
    }

    LocalDate getCoveredFrom() {
        return coveredFrom;
    }

    LocalDate getCheckedOn() {
        return checkedOn;
    }
}
