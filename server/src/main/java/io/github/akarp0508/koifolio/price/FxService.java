package io.github.akarp0508.koifolio.price;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Converts at the latest rate. Other pairs are cross rates through PLN, the currency NBP quotes against. */
@Service
public class FxService {

    private static final Logger log = LoggerFactory.getLogger(FxService.class);
    private static final String PLN = "PLN";
    /** NBP publishes once a working day, so there's no point in asking every run. */
    private static final Duration REFRESH_EVERY = Duration.ofHours(1);

    private final FxProvider provider;
    private final FxRateRepository rates;

    FxService(FxProvider provider, FxRateRepository rates) {
        this.provider = provider;
        this.rates = rates;
    }

    /** The latest rates, read once, for converting many amounts. */
    public final class Rates {

        private final Map<String, BigDecimal> toPln = new HashMap<>();

        private Rates() {
            toPln.put(PLN, BigDecimal.ONE);
            rates.findAll().forEach(rate -> toPln.put(rate.getCurrency(), rate.getRate()));
        }

        /** Empty if either currency has no rate. */
        public Optional<BigDecimal> convert(BigDecimal amount, String from, String to) {
            if (from.equals(to)) {
                return Optional.of(amount);
            }
            BigDecimal fromRate = toPln.get(from);
            BigDecimal toRate = toPln.get(to);
            if (fromRate == null || toRate == null) {
                return Optional.empty();
            }
            return Optional.of(amount.multiply(fromRate, MathContext.DECIMAL128).divide(toRate, MathContext.DECIMAL128));
        }

        public List<String> currencies() {
            return List.copyOf(new TreeSet<>(toPln.keySet()));
        }
    }

    @Transactional(readOnly = true)
    public Rates rates() {
        return new Rates();
    }

    /** A failure only logs: the last rates stay in place. Never runs inside a transaction: it does network calls. */
    void refresh() {
        Instant now = Instant.now();
        boolean fresh = rates.findAll().stream().anyMatch(rate -> rate.getFetchedAt().plus(REFRESH_EVERY).isAfter(now));
        if (fresh) {
            return;
        }
        try {
            rates.saveAll(provider.latestRates().stream()
                    .map(rate -> new FxRate(rate.currency(), rate.rate(), rate.effectiveDate(), now))
                    .toList());
        } catch (RuntimeException e) {
            log.warn("Fetching FX rates failed, keeping the last ones: {}", e.toString());
        }
    }
}
