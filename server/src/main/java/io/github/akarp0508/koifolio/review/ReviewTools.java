package io.github.akarp0508.koifolio.review;

import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
class ReviewTools {

    private final ReviewService review;

    ReviewTools(ReviewService review) {
        this.review = review;
    }

    /** @param proposed left out when the change removes something */
    record PendingToolResponse(String agentName, String summary, Object proposed) {
    }

    @McpTool(name = "list_pending_changes", description = """
            Changes waiting for the user, which haven't applied yet. Check it before proposing the same thing again.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<PendingToolResponse> listPendingChanges(McpTransportContext context) {
        return review.list(AgentCaller.of(context).userId()).stream()
                .map(pending -> new PendingToolResponse(pending.agent(), pending.summary(), pending.changes())).toList();
    }
}
