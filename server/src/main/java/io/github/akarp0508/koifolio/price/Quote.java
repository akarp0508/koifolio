package io.github.akarp0508.koifolio.price;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** The latest price of a provider symbol. */
@Entity
@Table(name = "quotes")
class Quote {

    @Id
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;

    @Column(name = "quoted_at", nullable = false)
    private Instant quotedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected Quote() {
    }

    Quote(String symbol, BigDecimal price, Instant quotedAt, Instant fetchedAt) {
        this.symbol = symbol;
        this.price = price;
        this.quotedAt = quotedAt;
        this.fetchedAt = fetchedAt;
    }

    String getSymbol() {
        return symbol;
    }

    BigDecimal getPrice() {
        return price;
    }

    Instant getQuotedAt() {
        return quotedAt;
    }
}
