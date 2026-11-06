package io.github.akarp0508.koifolio.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A validated transaction: only the fields its type uses are set. */
record TransactionValues(TransactionType type, LocalDate date, String ticker, BigDecimal quantity, BigDecimal price,
                         BigDecimal fee, BigDecimal amount, BigDecimal withheldTax, BigDecimal cashEffect,
                         boolean affectsBalance, BigDecimal splitFrom, BigDecimal splitTo, String parentTicker, BigDecimal costShare) {

    TransactionValues(TransactionType type, LocalDate date, String ticker, BigDecimal quantity, BigDecimal price, BigDecimal fee,
                      BigDecimal amount, BigDecimal withheldTax, BigDecimal cashEffect, boolean affectsBalance, BigDecimal splitFrom,
                      BigDecimal splitTo) {
        this(type, date, ticker, quantity, price, fee, amount, withheldTax, cashEffect, affectsBalance, splitFrom, splitTo, null, null);
    }
}
