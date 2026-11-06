package io.github.akarp0508.koifolio.rule;

import io.github.akarp0508.koifolio.domain.RuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A limit of the user's own, on one account or, with no account, on everything. */
@Entity
@Table(name = "rules")
class Rule {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_id")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleType type;

    @Column(name = "limit_percent", nullable = false)
    private BigDecimal limitPercent;

    @Column(name = "max_risk")
    private Integer maxRisk;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Rule() {
    }

    Rule(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    void update(UUID accountId, RuleType type, BigDecimal limitPercent, Integer maxRisk) {
        this.accountId = accountId;
        this.type = type;
        this.limitPercent = limitPercent;
        this.maxRisk = maxRisk;
    }

    UUID getId() {
        return id;
    }

    UUID getAccountId() {
        return accountId;
    }

    RuleType getType() {
        return type;
    }

    BigDecimal getLimitPercent() {
        return limitPercent;
    }

    Integer getMaxRisk() {
        return maxRisk;
    }
}
