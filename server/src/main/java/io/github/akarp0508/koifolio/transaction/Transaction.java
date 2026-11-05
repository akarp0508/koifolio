package io.github.akarp0508.koifolio.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
class Transaction {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private LocalDate date;

    private String ticker;

    @Column(precision = 23, scale = 8)
    private BigDecimal quantity;

    @Column(precision = 19, scale = 6)
    private BigDecimal price;

    @Column(precision = 19, scale = 4)
    private BigDecimal fee;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "withheld_tax", precision = 19, scale = 4)
    private BigDecimal withheldTax;

    /** What this transaction does to cash: negative for a buy, for example. */
    @Column(name = "cash_effect", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashEffect;

    @Column(name = "affects_balance", nullable = false)
    private boolean affectsBalance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    Transaction(UUID userId, UUID accountId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.accountId = accountId;
        this.createdAt = Instant.now();
    }

    void apply(TransactionValues values) {
        type = values.type();
        date = values.date();
        ticker = values.ticker();
        quantity = values.quantity();
        price = values.price();
        fee = values.fee();
        amount = values.amount();
        withheldTax = values.withheldTax();
        cashEffect = values.cashEffect();
        affectsBalance = values.affectsBalance();
    }

    /** What this transaction currently contributes to the account balance. */
    BigDecimal balanceEffect() {
        return affectsBalance ? cashEffect : BigDecimal.ZERO;
    }

    UUID getId() {
        return id;
    }

    UUID getAccountId() {
        return accountId;
    }

    TransactionType getType() {
        return type;
    }

    LocalDate getDate() {
        return date;
    }

    String getTicker() {
        return ticker;
    }

    BigDecimal getQuantity() {
        return quantity;
    }

    BigDecimal getPrice() {
        return price;
    }

    BigDecimal getFee() {
        return fee;
    }

    BigDecimal getAmount() {
        return amount;
    }

    BigDecimal getWithheldTax() {
        return withheldTax;
    }

    BigDecimal getCashEffect() {
        return cashEffect;
    }

    boolean isAffectsBalance() {
        return affectsBalance;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
