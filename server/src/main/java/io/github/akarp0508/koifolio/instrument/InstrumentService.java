package io.github.akarp0508.koifolio.instrument;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.auth.AuthService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every lookup is scoped to the user: the list is private. */
@Service
public class InstrumentService {

    private final InstrumentRepository instruments;
    private final AuthService authService;

    InstrumentService(InstrumentRepository instruments, AuthService authService) {
        this.instruments = instruments;
        this.authService = authService;
    }

    public static String normalizeTicker(String ticker) {
        return ticker.strip().toUpperCase(Locale.ROOT);
    }

    /** The instrument for a ticker the user just used, putting it on their list if needed. */
    @Transactional
    public UUID useTicker(UUID userId, String ticker) {
        return instruments.listAndGetId(userId, ticker);
    }

    /** Every ticker the user has by instrument id, including ones removed from their list. */
    @Transactional(readOnly = true)
    public Map<UUID, String> tickers(UUID userId) {
        return instruments.findByUserId(userId).stream()
                .collect(Collectors.toMap(Instrument::getId, Instrument::getTicker));
    }

    public record QuoteRef(String symbol, String currency) {
    }

    public record Exported(String ticker, InstrumentDetails details, boolean listed) {
    }

    @Transactional(readOnly = true)
    public List<Exported> exportAll(UUID userId) {
        return instruments.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Instrument::getTicker))
                .map(instrument -> new Exported(instrument.getTicker(), instrument.details(), instrument.isListed()))
                .toList();
    }

    /** Runs after the transactions, which may have added the ticker already. */
    @Transactional
    public void restore(UUID userId, Exported exported) {
        String ticker = normalizeTicker(exported.ticker());
        Instrument instrument = instruments.findByUserIdAndTicker(userId, ticker)
                .orElseGet(() -> new Instrument(userId, ticker));
        instrument.describe(exported.details());
        instrument.setListed(exported.listed());
        instruments.save(instrument);
    }

    public record InstrumentView(UUID id, String ticker, InstrumentDetails details) {
    }

    /** Everything the user has by instrument id, including ones removed from their list. */
    @Transactional(readOnly = true)
    public Map<UUID, InstrumentView> views(UUID userId) {
        return instruments.findByUserId(userId).stream().collect(Collectors.toMap(Instrument::getId,
                instrument -> new InstrumentView(instrument.getId(), instrument.getTicker(), instrument.details())));
    }

    /** The price source's symbol for each of the user's instruments that has one. */
    @Transactional(readOnly = true)
    public Map<UUID, QuoteRef> quoteRefs(UUID userId) {
        return instruments.findByUserId(userId).stream()
                .filter(instrument -> instrument.getQuoteSymbol() != null)
                .collect(Collectors.toMap(Instrument::getId, instrument ->
                        new QuoteRef(instrument.getQuoteSymbol(), instrument.getQuoteCurrency())));
    }

    /** Tickers with no symbol yet, skipping ones already asked about since {@code checkedBefore}. */
    @Transactional(readOnly = true)
    public List<String> tickersToResolve(Instant checkedBefore, int limit) {
        return instruments.findTickersToResolve(checkedBefore, Limit.of(limit));
    }

    /** @param symbol null if the source doesn't cover the ticker */
    @Transactional
    public void saveResolution(String ticker, String symbol, String isin, String currency, Instant checkedAt) {
        instruments.saveResolution(ticker, symbol, isin, currency, checkedAt);
    }

    @Transactional(readOnly = true)
    public List<String> quoteSymbols() {
        return instruments.findQuoteSymbols();
    }

    @Transactional(readOnly = true)
    List<Instrument> list(UUID userId) {
        authService.currentUser(userId);
        return instruments.findByUserIdAndListedTrueOrderByTicker(userId);
    }

    @Transactional
    Instrument create(UUID userId, String ticker, InstrumentDetails details) {
        authService.currentUser(userId);
        String normalized = normalizeTicker(ticker);
        Optional<Instrument> existing = instruments.findByUserIdAndTicker(userId, normalized);
        if (existing.isPresent() && !existing.get().isListed()) {
            existing.get().setListed(true);
            existing.get().describe(details);
            return existing.get();
        }
        Instrument instrument = new Instrument(userId, normalized);
        instrument.describe(details);
        try {
            return instruments.saveAndFlush(instrument);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "instrument_exists", "This ticker is already on your list");
        }
    }

    @Transactional
    Instrument update(UUID userId, UUID id, InstrumentDetails details) {
        authService.currentUser(userId);
        Instrument instrument = instruments.findByIdAndUserId(id, userId).orElseThrow(InstrumentService::notFound);
        instrument.describe(details);
        return instrument;
    }

    @Transactional(readOnly = true)
    Instrument get(UUID userId, UUID id) {
        authService.currentUser(userId);
        return instruments.findByIdAndUserId(id, userId).orElseThrow(InstrumentService::notFound);
    }

    @Transactional
    void delete(UUID userId, UUID id) {
        authService.currentUser(userId);
        find(userId, id).setListed(false);
    }

    private Instrument find(UUID userId, UUID id) {
        return instruments.findByIdAndUserIdAndListedTrue(id, userId).orElseThrow(InstrumentService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "instrument_not_found", "Instrument not found");
    }
}
