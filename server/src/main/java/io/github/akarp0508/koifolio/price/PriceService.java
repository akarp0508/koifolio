package io.github.akarp0508.koifolio.price;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.instrument.InstrumentService;
import io.github.akarp0508.koifolio.instrument.InstrumentService.QuoteRef;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps quotes up to date. A failed fetch only logs: the last known prices stay in place. */
@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);
    /** New tickers per run, so a big import doesn't flood the source. */
    private static final int RESOLVE_LIMIT = 20;
    private static final Duration RESOLVE_RETRY = Duration.ofDays(1);

    private final QuoteProvider provider;
    private final QuoteRepository quotes;
    private final InstrumentService instruments;
    private final PriceHistoryService history;
    private final Duration cooldown;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile Instant lastManualRefresh = Instant.EPOCH;

    PriceService(QuoteProvider provider, QuoteRepository quotes, InstrumentService instruments,
                 PriceHistoryService history, @Value("${koifolio.prices.refresh-cooldown}") Duration cooldown) {
        this.provider = provider;
        this.quotes = quotes;
        this.instruments = instruments;
        this.history = history;
        this.cooldown = cooldown;
    }

    public record CurrentPrice(BigDecimal price, String currency, Instant quotedAt) {
    }

    /** The current price of each of the user's instruments that has one. */
    @Transactional(readOnly = true)
    public Map<UUID, CurrentPrice> currentPrices(UUID userId) {
        Map<UUID, QuoteRef> refs = instruments.quoteRefs(userId);
        Map<String, Quote> bySymbol = new HashMap<>();
        quotes.findAllById(refs.values().stream().map(QuoteRef::symbol).distinct().toList())
                .forEach(quote -> bySymbol.put(quote.getSymbol(), quote));

        Map<UUID, CurrentPrice> prices = new HashMap<>();
        refs.forEach((instrumentId, ref) -> {
            Quote quote = bySymbol.get(ref.symbol());
            if (quote != null) {
                prices.put(instrumentId, new CurrentPrice(quote.getPrice(), ref.currency(), quote.getQuotedAt()));
            }
        });
        return prices;
    }

    /** The Refresh button. The cooldown protects the source however many users there are. */
    public void refreshNow() {
        Instant now = Instant.now();
        if (lastManualRefresh.plus(cooldown).isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "refresh_too_soon",
                    "Prices were refreshed a moment ago. Try again shortly.");
        }
        lastManualRefresh = now;
        refresh();
    }

    void refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            resolveNewTickers();
            fetchQuotes();
        } catch (RuntimeException e) {
            log.warn("Price refresh failed, keeping the last known prices: {}", e.toString());
        }
        try {
            history.sync();
        } finally {
            refreshing.set(false);
        }
    }

    private void resolveNewTickers() {
        Instant now = Instant.now();
        for (String ticker : instruments.tickersToResolve(now.minus(RESOLVE_RETRY), RESOLVE_LIMIT)) {
            Optional<QuoteProvider.ResolvedSymbol> resolved = provider.resolve(ticker);
            instruments.saveResolution(ticker, resolved.map(QuoteProvider.ResolvedSymbol::symbol).orElse(null),
                    resolved.map(QuoteProvider.ResolvedSymbol::isin).orElse(null),
                    resolved.map(QuoteProvider.ResolvedSymbol::currency).orElse(null), now);
        }
    }

    private void fetchQuotes() {
        List<String> symbols = instruments.quoteSymbols();
        if (symbols.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<Quote> fetched = provider.quotes(symbols).entrySet().stream()
                .map(entry -> new Quote(entry.getKey(), entry.getValue().price(), entry.getValue().quotedAt(), now))
                .toList();
        quotes.saveAll(fetched);
        log.info("Fetched {} of {} prices", fetched.size(), symbols.size());
    }
}
