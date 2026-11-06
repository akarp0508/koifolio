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
import org.hibernate.annotations.SQLRestriction;

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "transactions")
public class Transaction {

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

    @Column(name = "instrument_id")
    private UUID instrumentId;

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

    @Column(name = "split_from", precision = 18, scale = 8)
    private BigDecimal splitFrom;

    @Column(name = "split_to", precision = 18, scale = 8)
    private BigDecimal splitTo;

    private String note;

    /** What this transaction does to cash: negative for a buy, for example. */
    @Column(name = "cash_effect", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashEffect;

    @Column(name = "affects_balance", nullable = false)
    private boolean affectsBalance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** The agent that added it, empty for the user. */
    @Column(name = "created_by")
    private String createdBy;

    /** Shared by what an agent added in one call. */
    @Column(name = "import_id")
    private UUID importId;

    /** Empty while it waits for review. */
    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Transaction() {
    }

    Transaction(UUID userId, UUID accountId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.accountId = accountId;
        this.createdAt = Instant.now();
        this.validatedAt = createdAt;
    }

    /** It counts straight away, but stays marked until the user approves it. */
    void addedBy(String agent, boolean unvalidated) {
        createdBy = agent;
        validatedAt = unvalidated ? null : validatedAt;
    }

    /** A blank note is no note. */
    void setNote(String note) {
        this.note = note == null || note.isBlank() ? null : note.strip();
    }

    String getNote() {
        return note;
    }

    void setImportId(UUID importId) {
        this.importId = importId;
    }

    UUID getImportId() {
        return importId;
    }

    void approve() {
        validatedAt = Instant.now();
    }

    void softDelete() {
        deletedAt = Instant.now();
    }

    boolean isUnvalidated() {
        return validatedAt == null;
    }

    String getCreatedBy() {
        return createdBy;
    }

    void apply(TransactionValues values, UUID instrumentId) {
        type = values.type();
        date = values.date();
        this.instrumentId = instrumentId;
        quantity = values.quantity();
        price = values.price();
        fee = values.fee();
        amount = values.amount();
        withheldTax = values.withheldTax();
        splitFrom = values.splitFrom();
        splitTo = values.splitTo();
        cashEffect = values.cashEffect();
        affectsBalance = values.affectsBalance();
    }

    /** Keeps the order of same-day transactions when they're restored. */
    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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

    UUID getInstrumentId() {
        return instrumentId;
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

    BigDecimal getSplitFrom() {
        return splitFrom;
    }

    BigDecimal getSplitTo() {
        return splitTo;
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
