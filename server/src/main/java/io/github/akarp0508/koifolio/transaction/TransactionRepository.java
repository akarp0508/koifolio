package io.github.akarp0508.koifolio.transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByUserId(UUID userId);

    List<Transaction> findByUserIdAndValidatedAtIsNull(UUID userId);

    int countByUserIdAndValidatedAtIsNull(UUID userId);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    /** Goes around the entity, which can't see deleted rows. */
    @Modifying
    @Query(value = "UPDATE transactions SET deleted_at = NULL WHERE id = ?1 AND user_id = ?2 AND deleted_at IS NOT NULL", nativeQuery = true)
    int undelete(UUID id, UUID userId);
}
