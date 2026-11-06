package io.github.akarp0508.koifolio.price;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** The latest rate of a currency: how many PLN one unit buys. */
@Entity
@Table(name = "fx_rates")
class FxRate {

    @Id
    private String currency;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected FxRate() {
    }

    FxRate(String currency, BigDecimal rate, LocalDate effectiveDate, Instant fetchedAt) {
        this.currency = currency;
        this.rate = rate;
        this.effectiveDate = effectiveDate;
        this.fetchedAt = fetchedAt;
    }

    String getCurrency() {
        return currency;
    }

    BigDecimal getRate() {
        return rate;
    }

    Instant getFetchedAt() {
        return fetchedAt;
    }
}
