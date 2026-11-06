package io.github.akarp0508.koifolio.bond;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "bond_lots")
class BondLot {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false)
    private String series;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "purchase_date", nullable = false, updatable = false)
    private LocalDate purchaseDate;

    @Column(nullable = false)
    private String rates;

    @Column(name = "early_fee", nullable = false, updatable = false)
    private BigDecimal earlyFee;

    @Column(name = "affects_balance", nullable = false, updatable = false)
    private boolean affectsBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BondLot() {
    }

    BondLot(UUID userId, BondService.Input input, BigDecimal earlyFee) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.accountId = input.accountId();
        this.series = input.series();
        this.quantity = input.quantity();
        this.purchaseDate = input.purchaseDate();
        this.earlyFee = earlyFee;
        this.affectsBalance = input.affectsBalance();
        this.createdAt = Instant.now();
        setRates(input.rates());
    }

    void setRates(List<BigDecimal> values) {
        rates = values.stream().map(BigDecimal::toPlainString).collect(Collectors.joining(","));
    }

    List<BigDecimal> getRates() {
        return Arrays.stream(rates.split(",")).map(BigDecimal::new).toList();
    }

    UUID getId() {
        return id;
    }

    UUID getAccountId() {
        return accountId;
    }

    String getSeries() {
        return series;
    }

    int getQuantity() {
        return quantity;
    }

    LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    BigDecimal getEarlyFee() {
        return earlyFee;
    }

    boolean isAffectsBalance() {
        return affectsBalance;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
