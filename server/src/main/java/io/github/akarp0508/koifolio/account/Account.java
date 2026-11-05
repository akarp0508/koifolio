package io.github.akarp0508.koifolio.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    /** Balances stay below this, to fit the column. */
    public static final BigDecimal BALANCE_LIMIT = new BigDecimal("1000000000000000");

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    /** The last day the balance was set by hand. Transactions dated before it are backfill by default. */
    @Column(name = "balance_set_on", nullable = false)
    private LocalDate balanceSetOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    Account(UUID userId, String name, String description, String currency, BigDecimal balance) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.currency = currency;
        this.balance = balance;
        this.balanceSetOn = LocalDate.now();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    LocalDate getBalanceSetOn() {
        return balanceSetOn;
    }

    void setName(String name) {
        this.name = name;
    }

    void setDescription(String description) {
        this.description = description;
    }

    /** Setting the balance by hand: an unchanged value doesn't count. */
    void setBalance(BigDecimal newBalance) {
        if (balance.compareTo(newBalance) != 0) {
            balance = newBalance;
            balanceSetOn = LocalDate.now();
        }
    }

    /** A transaction changing the balance. */
    public void adjustBalance(BigDecimal delta) {
        balance = balance.add(delta);
    }
}
