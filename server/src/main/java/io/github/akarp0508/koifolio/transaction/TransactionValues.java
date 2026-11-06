package io.github.akarp0508.koifolio.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A validated transaction: only the fields its type uses are set. */
record TransactionValues(TransactionType type, LocalDate date, String ticker, BigDecimal quantity, BigDecimal price,
                         BigDecimal fee, BigDecimal amount, BigDecimal withheldTax, BigDecimal cashEffect,
                         boolean affectsBalance, BigDecimal splitFrom, BigDecimal splitTo) {
}
