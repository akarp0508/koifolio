package io.github.akarp0508.koifolio.price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A source of exchange rates against PLN. Does network calls, so never call it inside a database transaction. */
interface FxProvider {

    List<Rate> latestRates();

    /** Every rate published from {@code from} to {@code to}, both included. */
    List<Rate> ratesBetween(LocalDate from, LocalDate to);

    /** @param rate how many PLN one unit buys */
    record Rate(String currency, BigDecimal rate, LocalDate effectiveDate) {
    }
}
