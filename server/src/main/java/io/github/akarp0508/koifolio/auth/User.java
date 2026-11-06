package io.github.akarp0508.koifolio.auth;

import io.github.akarp0508.koifolio.domain.CostFxRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "base_currency", nullable = false)
    private String baseCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_fx_rule", nullable = false)
    private CostFxRule costFxRule;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String username, String passwordHash) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.baseCurrency = "PLN";
        this.costFxRule = CostFxRule.PREVIOUS_PUBLICATION;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public CostFxRule getCostFxRule() {
        return costFxRule;
    }

    void setCostFxRule(CostFxRule costFxRule) {
        this.costFxRule = costFxRule;
    }

    void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }
}
