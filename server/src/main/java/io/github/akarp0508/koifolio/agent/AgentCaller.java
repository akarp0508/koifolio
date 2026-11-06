package io.github.akarp0508.koifolio.agent;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.audit.Actor;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Who a tool is running for. This is all tools know about a caller, so any way of logging in can
 * produce one.
 *
 * @param name the agent's name, for the change log
 */
public record AgentCaller(UUID userId, String name, Set<AgentScope> scopes) {

    static final String CONTEXT_KEY = "koifolio.agent";

    public static AgentCaller of(McpTransportContext context) {
        return (AgentCaller) context.get(CONTEXT_KEY);
    }

    public Actor actor() {
        return Actor.agent(name);
    }

    /** Stops a tool this token wasn't given. */
    public AgentCaller require(AgentScope scope) {
        if (!scopes.contains(scope)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "agent_scope_missing",
                    "This token isn't allowed to do that. Its owner can create one with the right permissions.");
        }
        return this;
    }
}
