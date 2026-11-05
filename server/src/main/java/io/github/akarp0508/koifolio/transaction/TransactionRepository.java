package io.github.akarp0508.koifolio.transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByUserId(UUID userId);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);
}
