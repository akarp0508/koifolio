package io.github.akarp0508.koifolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyClose(LocalDate date, BigDecimal close) {
}
