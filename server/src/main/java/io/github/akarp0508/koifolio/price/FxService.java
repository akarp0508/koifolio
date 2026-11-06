package io.github.akarp0508.koifolio.price;

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Converts at the latest rate. Other pairs are cross rates through PLN, the currency NBP quotes against. */
@Service
public class FxService {

    private static final Logger log = LoggerFactory.getLogger(FxService.class);
    private static final String PLN = "PLN";
    /** NBP publishes once a working day, so there's no point in asking every run. */
    private static final Duration REFRESH_EVERY = Duration.ofHours(1);

    /** The status row for FX history, kept with the symbols' ones. */
    private static final String HISTORY_STATUS = "FX:NBP";
    private static final int OVERLAP_DAYS = 7;

    private final FxProvider provider;
    private final FxRateRepository rates;
    private final PriceHistoryRepository statuses;
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaction;

    FxService(FxProvider provider, FxRateRepository rates, PriceHistoryRepository statuses, JdbcClient jdbc,
              JdbcTemplate jdbcTemplate, TransactionTemplate transaction) {
        this.provider = provider;
        this.rates = rates;
        this.statuses = statuses;
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.transaction = transaction;
    }

    /** Past rates, read once. Falls back to the latest rate before history reaches a date. */
    public final class History {

        private final Map<String, NavigableMap<LocalDate, BigDecimal>> toPln = new HashMap<>();
        private final Rates latest = new Rates();

        private History() {
            jdbc.sql("SELECT currency, date, rate FROM fx_history").query(row -> {
                toPln.computeIfAbsent(row.getString("currency"), code -> new TreeMap<>())
                        .put(row.getDate("date").toLocalDate(), row.getBigDecimal("rate"));
            });
        }

        /** At the last rate published on or before {@code date}. Empty if either currency has no rate at all. */
        public Optional<BigDecimal> convert(BigDecimal amount, String from, String to, LocalDate date) {
            if (from.equals(to)) {
                return Optional.of(amount);
            }
            Optional<BigDecimal> fromRate = rateOn(from, date);
            Optional<BigDecimal> toRate = rateOn(to, date);
            if (fromRate.isEmpty() || toRate.isEmpty()) {
                return latest.convert(amount, from, to);
            }
            return Optional.of(amount.multiply(fromRate.get(), MathContext.DECIMAL128)
                    .divide(toRate.get(), MathContext.DECIMAL128));
        }

        private Optional<BigDecimal> rateOn(String currency, LocalDate date) {
            if (currency.equals(PLN)) {
                return Optional.of(BigDecimal.ONE);
            }
            return Optional.ofNullable(toPln.get(currency)).map(rates -> rates.floorEntry(date)).map(Map.Entry::getValue);
        }
    }

    @Transactional(readOnly = true)
    public History history() {
        return new History();
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
        refreshLatest();
        syncHistory();
    }

    /** Self-repairing, like closes: it compares what's stored with the earliest trade anyone has. */
    private void syncHistory() {
        LocalDate today = LocalDate.now(PriceHistoryService.MARKET_ZONE);
        try {
            Optional<LocalDate> needed = Optional.ofNullable(statuses.findEarliestTrade());
            Optional<PriceHistory> status = statuses.findById(HISTORY_STATUS);
            if (needed.isEmpty() || (status.isPresent() && status.get().getCheckedOn().equals(today)
                    && !needed.get().isBefore(status.get().getCoveredFrom()))) {
                return;
            }
            boolean reachesBack = status.isEmpty() || needed.get().isBefore(status.get().getCoveredFrom());
            LocalDate earliest = (reachesBack ? needed.get() : status.get().getCheckedOn()).minusDays(OVERLAP_DAYS);
            List<FxProvider.Rate> fetched = provider.ratesBetween(earliest, today);
            transaction.executeWithoutResult(unused -> {
                jdbcTemplate.batchUpdate("""
                        INSERT INTO fx_history (currency, date, rate) VALUES (?, ?, ?)
                        ON CONFLICT (currency, date) DO UPDATE SET rate = excluded.rate""",
                        fetched, 500, (statement, rate) -> {
                            statement.setString(1, rate.currency());
                            statement.setDate(2, Date.valueOf(rate.effectiveDate()));
                            statement.setBigDecimal(3, rate.rate());
                        });
                LocalDate coveredFrom = status.map(PriceHistory::getCoveredFrom)
                        .filter(existing -> existing.isBefore(earliest)).orElse(earliest);
                statuses.save(new PriceHistory(HISTORY_STATUS, coveredFrom, today));
            });
        } catch (RuntimeException e) {
            log.warn("Fetching FX history failed, trying again next run: {}", e.toString());
        }
    }

    private void refreshLatest() {
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
