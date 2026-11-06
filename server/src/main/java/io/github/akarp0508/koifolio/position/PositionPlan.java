package io.github.akarp0508.koifolio.position;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** What the user intends with an instrument in one account. */
@Entity
@Table(name = "position_plans")
@IdClass(PositionPlan.Key.class)
public class PositionPlan {

    record Key(UUID accountId, UUID instrumentId) implements Serializable {
    }

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Id
    @Column(name = "instrument_id")
    private UUID instrumentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String plan;

    private Short risk;

    @Column(name = "target_price", precision = 19, scale = 6)
    private BigDecimal targetPrice;

    @Column(name = "stop_price", precision = 19, scale = 6)
    private BigDecimal stopPrice;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "position_tags", joinColumns = {
            @JoinColumn(name = "account_id", referencedColumnName = "account_id"),
            @JoinColumn(name = "instrument_id", referencedColumnName = "instrument_id")})
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    protected PositionPlan() {
    }

    PositionPlan(UUID accountId, UUID instrumentId, UUID userId) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.userId = userId;
    }

    void update(String plan, Integer risk, BigDecimal targetPrice, BigDecimal stopPrice, Set<String> newTags) {
        tags.clear();
        tags.addAll(newTags);
        this.plan = plan == null ? "" : plan;
        this.risk = risk == null ? null : risk.shortValue();
        this.targetPrice = targetPrice;
        this.stopPrice = stopPrice;
    }

    UUID getAccountId() {
        return accountId;
    }

    UUID getInstrumentId() {
        return instrumentId;
    }

    String getPlan() {
        return plan;
    }

    Integer getRisk() {
        return risk == null ? null : risk.intValue();
    }

    BigDecimal getTargetPrice() {
        return targetPrice;
    }

    BigDecimal getStopPrice() {
        return stopPrice;
    }

    Set<String> getTags() {
        return Set.copyOf(tags);
    }
}
