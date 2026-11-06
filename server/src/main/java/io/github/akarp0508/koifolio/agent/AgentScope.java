package io.github.akarp0508.koifolio.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** What a token may change. Reading needs no scope, since every token can read. */
public enum AgentScope {
    @JsonProperty("analysis:write") ANALYSIS_WRITE,
    @JsonProperty("transactions:write") TRANSACTIONS_WRITE,
    @JsonProperty("accounts:write") ACCOUNTS_WRITE,
    @JsonProperty("rules:write") RULES_WRITE;

    static final Set<AgentScope> DEFAULTS = EnumSet.of(ANALYSIS_WRITE, TRANSACTIONS_WRITE, ACCOUNTS_WRITE);

    static String store(Set<AgentScope> scopes) {
        return scopes.stream().map(Enum::name).collect(Collectors.joining(" "));
    }

    static Set<AgentScope> load(String stored) {
        Set<AgentScope> scopes = EnumSet.noneOf(AgentScope.class);
        for (String scope : stored.split(" ")) {
            if (!scope.isEmpty()) {
                scopes.add(valueOf(scope));
            }
        }
        return scopes;
    }
}
