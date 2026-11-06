package io.github.akarp0508.koifolio.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Lets one agent act for its user through MCP, and nowhere else. */
@Entity
@Table(name = "agent_tokens")
class AgentToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private String scopes;

    @Column(name = "expires_on", updatable = false)
    private LocalDate expiresOn;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentToken() {
    }

    AgentToken(UUID userId, String name, String tokenHash, Set<AgentScope> scopes, LocalDate expiresOn) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.tokenHash = tokenHash;
        this.scopes = AgentScope.store(scopes);
        this.expiresOn = expiresOn;
        this.createdAt = Instant.now();
    }

    boolean expired(LocalDate today) {
        return expiresOn != null && today.isAfter(expiresOn);
    }

    void used() {
        lastUsedAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    String getName() {
        return name;
    }

    Set<AgentScope> getScopes() {
        return AgentScope.load(scopes);
    }

    LocalDate getExpiresOn() {
        return expiresOn;
    }

    Instant getLastUsedAt() {
        return lastUsedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
