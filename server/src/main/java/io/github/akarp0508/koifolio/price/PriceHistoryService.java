package io.github.akarp0508.koifolio.price;

import io.github.akarp0508.koifolio.domain.DailyClose;
import io.github.akarp0508.koifolio.price.PriceHistoryRepository.Needed;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps daily closes stored locally. It looks at what's needed rather than at what ran before, so
 * downtime and backfilled transactions both repair themselves on the next run.
 */
@Service
public class PriceHistoryService {

    /** A day counts as finished once it's over here, after both Warsaw and New York have closed. */
    public static final ZoneId MARKET_ZONE = ZoneId.of("Europe/Warsaw");

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryService.class);
    /** Symbols per run, so a big import spreads out over several runs. */
    private static final int SYNC_LIMIT = 20;
    /** Re-read a few recent days each time, in case the source corrected them. */
    private static final int OVERLAP_DAYS = 7;

    private final QuoteProvider provider;
    private final PriceHistoryRepository statuses;
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaction;

    PriceHistoryService(QuoteProvider provider, PriceHistoryRepository statuses, JdbcClient jdbc,
                        JdbcTemplate jdbcTemplate, TransactionTemplate transaction) {
        this.provider = provider;
        this.statuses = statuses;
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.transaction = transaction;
    }

    /** Oldest first. */
    @Transactional(readOnly = true)
    public List<DailyClose> closes(String symbol) {
        return jdbc.sql("SELECT date, close FROM daily_closes WHERE symbol = ? ORDER BY date")
                .param(symbol)
                .query((row, index) -> new DailyClose(row.getDate("date").toLocalDate(), row.getBigDecimal("close")))
                .list();
    }

    /** A failure only skips that symbol until the next run. Never runs inside a transaction: it does network calls. */
    void sync() {
        LocalDate today = LocalDate.now(Clock.system(MARKET_ZONE));
        int synced = 0;
        for (Needed needed : statuses.findNeeded()) {
            Optional<LocalDate> start = fetchFrom(needed, today);
            if (start.isEmpty()) {
                continue;
            }
            if (synced++ == SYNC_LIMIT) {
                return;
            }
            try {
                List<DailyClose> closes = provider.dailyCloses(needed.symbol(), needed.isin(), start.get(), today);
                store(needed, closes, today);
            } catch (RuntimeException e) {
                log.warn("Fetching closes for {} failed, trying again next run: {}", needed.symbol(), e.toString());
            }
        }
    }

    /** Where to start fetching, or nothing if this symbol is up to date. */
    private Optional<LocalDate> fetchFrom(Needed needed, LocalDate today) {
        Optional<PriceHistory> status = statuses.findById(needed.symbol());
        if (status.isEmpty() || needed.firstTrade().isBefore(status.get().getCoveredFrom())) {
            return Optional.of(needed.firstTrade());
        }
        if (status.get().getCheckedOn().isBefore(today)) {
            return Optional.of(status.get().getCheckedOn().minusDays(OVERLAP_DAYS));
        }
        return Optional.empty();
    }

    private void store(Needed needed, List<DailyClose> closes, LocalDate today) {
        transaction.executeWithoutResult(unused -> {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO daily_closes (symbol, date, close) VALUES (?, ?, ?)
                    ON CONFLICT (symbol, date) DO UPDATE SET close = excluded.close""",
                    closes, 500, (statement, close) -> {
                        statement.setString(1, needed.symbol());
                        statement.setDate(2, Date.valueOf(close.date()));
                        statement.setBigDecimal(3, close.close());
                    });
            LocalDate coveredFrom = statuses.findById(needed.symbol())
                    .map(PriceHistory::getCoveredFrom)
                    .filter(existing -> existing.isBefore(needed.firstTrade()))
                    .orElse(needed.firstTrade());
            statuses.save(new PriceHistory(needed.symbol(), coveredFrom, today));
        });
    }
}
