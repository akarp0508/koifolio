package io.github.akarp0508.koifolio.instrument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface InstrumentRepository extends JpaRepository<Instrument, UUID> {

    List<Instrument> findByUserIdAndListedTrueOrderByTicker(UUID userId);

    List<Instrument> findByUserId(UUID userId);

    Optional<Instrument> findByUserIdAndTicker(UUID userId, String ticker);

    Optional<Instrument> findByIdAndUserIdAndListedTrue(UUID id, UUID userId);

    /** Safe when two requests use the same new ticker at once. */
    @Query(nativeQuery = true, value = """
            INSERT INTO instruments (id, user_id, ticker, created_at, listed)
            VALUES (gen_random_uuid(), :userId, :ticker, now(), true)
            ON CONFLICT (user_id, ticker) DO UPDATE SET listed = true
            RETURNING id""")
    UUID listAndGetId(UUID userId, String ticker);
}
