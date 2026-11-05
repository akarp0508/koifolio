package io.github.akarp0508.koifolio.instrument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A ticker a user holds or follows. Removing one from their list only clears {@code listed}, for its transactions. */
@Entity
@Table(name = "instruments")
class Instrument {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean listed;

    /** The price source's own symbol, once found. */
    @Column(name = "quote_symbol")
    private String quoteSymbol;

    @Column(name = "quote_currency")
    private String quoteCurrency;

    /** When the source was last asked about this ticker. */
    @Column(name = "quote_checked_at")
    private Instant quoteCheckedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Instrument() {
    }

    Instrument(UUID userId, String ticker, String name) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.ticker = ticker;
        this.name = name;
        this.listed = true;
        this.createdAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    String getTicker() {
        return ticker;
    }

    String getName() {
        return name;
    }

    String getQuoteSymbol() {
        return quoteSymbol;
    }

    String getQuoteCurrency() {
        return quoteCurrency;
    }

    boolean isListed() {
        return listed;
    }

    void setName(String name) {
        this.name = name;
    }

    void setListed(boolean listed) {
        this.listed = listed;
    }
}
