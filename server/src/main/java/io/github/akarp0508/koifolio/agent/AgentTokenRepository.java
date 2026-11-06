package io.github.akarp0508.koifolio.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AgentTokenRepository extends JpaRepository<AgentToken, UUID> {

    List<AgentToken> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<AgentToken> findByIdAndUserId(UUID id, UUID userId);

    Optional<AgentToken> findByTokenHash(String tokenHash);

    boolean existsByUserIdAndName(UUID userId, String name);
}
