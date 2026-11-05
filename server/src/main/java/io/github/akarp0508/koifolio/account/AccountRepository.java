package io.github.akarp0508.koifolio.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);
}
