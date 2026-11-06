package io.github.akarp0508.koifolio.instrument;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.audit.Actor;
import io.github.akarp0508.koifolio.audit.AuditLog;
import io.github.akarp0508.koifolio.auth.AuthService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final AuditLog auditLog;

    InstrumentService(InstrumentRepository instruments, AuthService authService, AuditLog auditLog) {
        this.auditLog = auditLog;
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

    /** Fields an agent wants to change. Null leaves a field alone. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Edit(String name, String sector, Integer risk, String thesis, String analysis, Set<String> tags) {

        private InstrumentDetails over(InstrumentDetails now) {
            return new InstrumentDetails(name == null ? now.name() : name, sector == null ? now.sector() : sector,
                    risk == null ? now.risk() : risk, thesis == null ? now.thesis() : thesis,
                    analysis == null ? now.analysis() : analysis, tags == null ? now.tags() : tags);
        }
    }

    @Transactional(readOnly = true)
    public Optional<InstrumentDetails> details(UUID userId, String ticker) {
        return instruments.findByUserIdAndTicker(userId, normalizeTicker(ticker)).map(Instrument::details);
    }

    /** Applies over whatever the company holds by now, so it keeps what was edited in the meantime. */
    @Transactional
    public void applyEdit(UUID userId, String ticker, Edit edit, Actor actor) {
        Optional<Instrument> existing = instruments.findByUserIdAndTicker(userId, normalizeTicker(ticker));
        if (existing.isPresent()) {
            update(userId, existing.get().getId(), edit.over(existing.get().details()), actor);
        } else {
            create(userId, ticker, edit.over(new InstrumentDetails(null, null, null, null, null, null)), actor);
        }
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
    Instrument create(UUID userId, String ticker, InstrumentDetails details, Actor actor) {
        authService.currentUser(userId);
        String normalized = normalizeTicker(ticker);
        Optional<Instrument> existing = instruments.findByUserIdAndTicker(userId, normalized);
        if (existing.isPresent() && !existing.get().isListed()) {
            InstrumentDetails before = existing.get().details();
            existing.get().setListed(true);
            existing.get().describe(details);
            log(userId, actor, existing.get(), "update", "Changed", before, details);
            return existing.get();
        }
        Instrument instrument = new Instrument(userId, normalized);
        instrument.describe(details);
        try {
            instruments.saveAndFlush(instrument);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "instrument_exists", "This ticker is already on your list");
        }
        log(userId, actor, instrument, "create", "Added", null, details);
        return instrument;
    }

    @Transactional
    public Instrument update(UUID userId, UUID id, InstrumentDetails details, Actor actor) {
        authService.currentUser(userId);
        Instrument instrument = instruments.findByIdAndUserId(id, userId).orElseThrow(InstrumentService::notFound);
        InstrumentDetails before = instrument.details();
        instrument.describe(details);
        log(userId, actor, instrument, "update", "Changed", before, details);
        return instrument;
    }

    @Transactional(readOnly = true)
    Instrument get(UUID userId, UUID id) {
        authService.currentUser(userId);
        return instruments.findByIdAndUserId(id, userId).orElseThrow(InstrumentService::notFound);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        authService.currentUser(userId);
        Instrument instrument = find(userId, id);
        instrument.setListed(false);
        log(userId, Actor.USER, instrument, "delete", "Removed", instrument.details(), null);
    }

    private void log(UUID userId, Actor actor, Instrument instrument, String action, String verb, InstrumentDetails before,
                     InstrumentDetails after) {
        auditLog.record(userId, actor, new AuditLog.Change("company", instrument.getId(), action,
                "%s %s".formatted(verb, instrument.getTicker()), before, after));
    }

    private Instrument find(UUID userId, UUID id) {
        return instruments.findByIdAndUserIdAndListedTrue(id, userId).orElseThrow(InstrumentService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "instrument_not_found", "Instrument not found");
    }
}
