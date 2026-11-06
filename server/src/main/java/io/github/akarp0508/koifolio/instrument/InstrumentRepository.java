package io.github.akarp0508.koifolio.instrument;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface InstrumentRepository extends JpaRepository<Instrument, UUID> {

    List<Instrument> findByUserIdAndListedTrueOrderByTicker(UUID userId);

    List<Instrument> findByUserId(UUID userId);

    Optional<Instrument> findByUserIdAndTicker(UUID userId, String ticker);

    Optional<Instrument> findByIdAndUserIdAndListedTrue(UUID id, UUID userId);

    @Query("""
            select distinct i.ticker from Instrument i
            where i.quoteSymbol is null and (i.quoteCheckedAt is null or i.quoteCheckedAt < :checkedBefore)
            order by i.ticker""")
    List<String> findTickersToResolve(Instant checkedBefore, Limit limit);

    /** The result applies to everyone using the ticker. */
    @Modifying
    @Query("""
            update Instrument i set i.quoteSymbol = :symbol, i.quoteIsin = :isin, i.quoteCurrency = :currency,
                i.quoteCheckedAt = :checkedAt
            where i.ticker = :ticker""")
    void saveResolution(String ticker, String symbol, String isin, String currency, Instant checkedAt);

    @Query("select distinct i.quoteSymbol from Instrument i where i.quoteSymbol is not null")
    List<String> findQuoteSymbols();

    /** Safe when two requests use the same new ticker at once. */
    @Query(nativeQuery = true, value = """
            INSERT INTO instruments (id, user_id, ticker, created_at, listed)
            VALUES (gen_random_uuid(), :userId, :ticker, now(), true)
            ON CONFLICT (user_id, ticker) DO UPDATE SET listed = true
            RETURNING id""")
    UUID listAndGetId(UUID userId, String ticker);
}
