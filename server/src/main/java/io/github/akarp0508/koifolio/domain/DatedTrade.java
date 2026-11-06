package io.github.akarp0508.koifolio.domain;

import java.time.LocalDate;

public record DatedTrade(LocalDate date, Trade trade) {
}
