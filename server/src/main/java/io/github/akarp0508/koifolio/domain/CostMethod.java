package io.github.akarp0508.koifolio.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CostMethod {
    @JsonProperty("average") AVERAGE,
    @JsonProperty("fifo") FIFO
}
