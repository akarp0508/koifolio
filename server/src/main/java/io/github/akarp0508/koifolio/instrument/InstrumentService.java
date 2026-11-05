package io.github.akarp0508.koifolio.instrument;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.auth.AuthService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Transactional(readOnly = true)
    List<Instrument> list(UUID userId) {
        authService.currentUser(userId);
        return instruments.findByUserIdAndListedTrueOrderByTicker(userId);
    }

    @Transactional
    Instrument create(UUID userId, String ticker, String name) {
        authService.currentUser(userId);
        String normalized = normalizeTicker(ticker);
        String cleanName = name == null ? "" : name.strip();
        Optional<Instrument> existing = instruments.findByUserIdAndTicker(userId, normalized);
        if (existing.isPresent() && !existing.get().isListed()) {
            existing.get().setListed(true);
            existing.get().setName(cleanName);
            return existing.get();
        }
        try {
            return instruments.saveAndFlush(new Instrument(userId, normalized, cleanName));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "instrument_exists", "This ticker is already on your list");
        }
    }

    @Transactional
    Instrument rename(UUID userId, UUID id, String name) {
        authService.currentUser(userId);
        Instrument instrument = find(userId, id);
        instrument.setName(name.strip());
        return instrument;
    }

    @Transactional
    void delete(UUID userId, UUID id) {
        authService.currentUser(userId);
        find(userId, id).setListed(false);
    }

    private Instrument find(UUID userId, UUID id) {
        return instruments.findByIdAndUserIdAndListedTrue(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "instrument_not_found", "Instrument not found"));
    }
}
