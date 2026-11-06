package io.github.akarp0508.koifolio.price;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** A split spotted in the source's history. The date is the first day seen after it, so it's approximate. */
@Entity
@Table(name = "split_suggestions")
public class SplitSuggestion {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "split_from", nullable = false)
    private int splitFrom;

    @Column(name = "split_to", nullable = false)
    private int splitTo;

    protected SplitSuggestion() {
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getSplitFrom() {
        return splitFrom;
    }

    public int getSplitTo() {
        return splitTo;
    }
}
