package io.github.akarp0508.koifolio.agent;

import io.modelcontextprotocol.common.McpTransportContext;
import java.util.UUID;

/**
 * Who a tool is running for. This is all tools know about a caller, so any way of logging in can
 * produce one.
 *
 * @param name the agent's name, for the change log
 */
public record AgentCaller(UUID userId, String name) {

    static final String CONTEXT_KEY = "koifolio.agent";

    public static AgentCaller of(McpTransportContext context) {
        return (AgentCaller) context.get(CONTEXT_KEY);
    }
}
