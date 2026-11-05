package io.github.akarp0508.koifolio.instrument;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.auth.AuthService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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

    /** Puts a ticker the user just used on their list, if it isn't there yet. */
    @Transactional
    public void addIfMissing(UUID userId, String ticker) {
        instruments.addIfMissing(userId, ticker);
    }

    @Transactional(readOnly = true)
    List<Instrument> list(UUID userId) {
        authService.currentUser(userId);
        return instruments.findByUserIdOrderByTicker(userId);
    }

    @Transactional
    Instrument create(UUID userId, String ticker, String name) {
        authService.currentUser(userId);
        try {
            return instruments.saveAndFlush(new Instrument(userId, normalizeTicker(ticker), name == null ? "" : name.strip()));
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
        instruments.delete(find(userId, id));
    }

    private Instrument find(UUID userId, UUID id) {
        return instruments.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "instrument_not_found", "Instrument not found"));
    }
}
