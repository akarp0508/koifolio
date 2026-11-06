package io.github.akarp0508.koifolio.transaction;

import io.github.akarp0508.koifolio.domain.Trade;
import java.time.LocalDate;
import java.util.UUID;

public record AccountTrade(UUID accountId, UUID instrumentId, LocalDate date, Trade trade) {
}
