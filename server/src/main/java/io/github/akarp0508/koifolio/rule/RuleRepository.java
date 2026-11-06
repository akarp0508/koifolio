package io.github.akarp0508.koifolio.rule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RuleRepository extends JpaRepository<Rule, UUID> {

    List<Rule> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);

    Optional<Rule> findByIdAndUserId(UUID id, UUID userId);
}
