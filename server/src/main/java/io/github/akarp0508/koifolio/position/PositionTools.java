package io.github.akarp0508.koifolio.position;

import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.position.PositionController.DetailsResponse;
import io.github.akarp0508.koifolio.position.PositionController.PositionResponse;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class PositionTools {

    private final PositionService positions;

    PositionTools(PositionService positions) {
        this.positions = positions;
    }

    @McpTool(name = "get_positions", description = """
            Holdings per account and ticker, in the account's currency: quantity, average cost, value, gains, dividends \
            and weights, with the base currency under inBase when it differs.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<PositionResponse> getPositions(McpTransportContext context,
                                        @McpToolParam(required = false, description = "Account id from list_accounts") UUID account,
                                        @McpToolParam(required = false, description = "Include fully sold positions") Boolean closed) {
        return PositionController.responses(positions, AgentCaller.of(context).userId(), account, Boolean.TRUE.equals(closed));
    }

    @McpTool(name = "get_plan", description = "The user's plan for one position: thesis, target, stop, risk and tags, with the company's details.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    DetailsResponse getPlan(McpTransportContext context,
                            @McpToolParam(description = "Account id from list_accounts") UUID account,
                            @McpToolParam(description = "Ticker") String ticker) {
        return DetailsResponse.of(positions.one(AgentCaller.of(context).userId(), account, ticker));
    }
}
