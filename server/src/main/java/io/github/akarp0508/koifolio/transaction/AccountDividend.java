package io.github.akarp0508.koifolio.transaction;

import io.github.akarp0508.koifolio.domain.Dividend;
import java.util.UUID;

public record AccountDividend(UUID accountId, UUID instrumentId, Dividend dividend) {
}
