package io.github.akarp0508.koifolio.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** A source of current prices. Implementations do network calls, so never call one inside a database transaction. */
interface QuoteProvider {

    /** Finds the source's own symbol for a ticker, or nothing if it doesn't cover it. */
    Optional<ResolvedSymbol> resolve(String ticker);

    /** Prices by symbol. Symbols the source doesn't know are left out. */
    Map<String, Quote> quotes(Collection<String> symbols);

    record ResolvedSymbol(String symbol, String currency) {
    }

    record Quote(BigDecimal price, Instant quotedAt) {
    }
}
