package io.github.akarp0508.koifolio.price;

import org.springframework.data.jpa.repository.JpaRepository;

interface FxRateRepository extends JpaRepository<FxRate, String> {
}
