package io.github.akarp0508.koifolio.position;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PositionPlanRepository extends JpaRepository<PositionPlan, PositionPlan.Key> {

    List<PositionPlan> findByUserId(UUID userId);
}
