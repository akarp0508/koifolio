package io.github.akarp0508.koifolio.bond;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BondLotRepository extends JpaRepository<BondLot, UUID> {

    List<BondLot> findByUserIdOrderByPurchaseDateAscIdAsc(UUID userId);

    Optional<BondLot> findByIdAndUserId(UUID id, UUID userId);
}
