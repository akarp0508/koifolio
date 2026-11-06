package io.github.akarp0508.koifolio.price;

import io.github.akarp0508.koifolio.domain.DailyClose;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A source of current prices. Implementations do network calls, so never call one inside a database transaction. */
interface QuoteProvider {

    /** Finds the source's own symbol for a ticker, or nothing if it doesn't cover it. */
    Optional<ResolvedSymbol> resolve(String ticker);

    /** Prices by symbol. Symbols the source doesn't know are left out. */
    Map<String, Quote> quotes(Collection<String> symbols);

    /** Closing prices oldest first, for {@code from} up to but not including {@code to}. */
    List<DailyClose> dailyCloses(String symbol, String isin, LocalDate from, LocalDate to);

    /** @param isin null for foreign stocks */
    record ResolvedSymbol(String symbol, String isin, String currency) {
    }

    record Quote(BigDecimal price, Instant quotedAt) {
    }
}
