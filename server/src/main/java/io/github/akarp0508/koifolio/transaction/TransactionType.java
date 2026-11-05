package io.github.akarp0508.koifolio.transaction;

import com.fasterxml.jackson.annotation.JsonProperty;

enum TransactionType {
    @JsonProperty("deposit") DEPOSIT,
    @JsonProperty("withdrawal") WITHDRAWAL,
    @JsonProperty("buy") BUY,
    @JsonProperty("sell") SELL,
    @JsonProperty("dividend") DIVIDEND,
    @JsonProperty("fee") FEE
}
