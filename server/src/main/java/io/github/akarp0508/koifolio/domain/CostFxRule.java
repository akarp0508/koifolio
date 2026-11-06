package io.github.akarp0508.koifolio.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/** Which day's rate converts a trade to the base currency. */
public enum CostFxRule {
    /** The last rate published before the trade: the Polish tax rule. */
    @JsonProperty("previous_publication") PREVIOUS_PUBLICATION,
    @JsonProperty("transaction_date") TRANSACTION_DATE;

    public LocalDate rateDate(LocalDate tradeDate) {
        return this == PREVIOUS_PUBLICATION ? tradeDate.minusDays(1) : tradeDate;
    }
}
