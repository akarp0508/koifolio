package io.github.akarp0508.koifolio.price;

import org.springframework.data.jpa.repository.JpaRepository;

interface QuoteRepository extends JpaRepository<Quote, String> {
}
