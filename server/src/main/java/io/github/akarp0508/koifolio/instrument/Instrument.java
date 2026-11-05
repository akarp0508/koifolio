package io.github.akarp0508.koifolio.instrument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A ticker on a user's own list. Transactions keep the ticker as text, so removing one loses nothing. */
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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Instrument() {
    }

    Instrument(UUID userId, String ticker, String name) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.ticker = ticker;
        this.name = name;
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

    void setName(String name) {
        this.name = name;
    }
}
