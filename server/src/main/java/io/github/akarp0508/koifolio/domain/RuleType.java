package io.github.akarp0508.koifolio.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RuleType {
    @JsonProperty("max_position_weight") MAX_POSITION_WEIGHT,
    @JsonProperty("min_low_risk_weight") MIN_LOW_RISK_WEIGHT
}
