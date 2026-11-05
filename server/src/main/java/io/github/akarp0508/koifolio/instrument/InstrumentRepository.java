package io.github.akarp0508.koifolio.instrument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface InstrumentRepository extends JpaRepository<Instrument, UUID> {

    List<Instrument> findByUserIdOrderByTicker(UUID userId);

    Optional<Instrument> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO instruments (id, user_id, ticker, created_at)
            VALUES (gen_random_uuid(), :userId, :ticker, now())
            ON CONFLICT (user_id, ticker) DO NOTHING""")
    void addIfMissing(UUID userId, String ticker);
}
