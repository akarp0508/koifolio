package io.github.akarp0508.koifolio.rule;

import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.rule.RuleController.RuleCheckResponse;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
class RuleTools {

    private final RuleService rules;

    RuleTools(RuleService rules) {
        this.rules = rules;
    }

    @McpTool(name = "check_rules", description = "The user's rules, each with whether it passes now and what's over the limit. They're indicators and aren't enforced.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<RuleCheckResponse> checkRules(McpTransportContext context) {
        return rules.check(AgentCaller.of(context).userId()).stream().map(RuleCheckResponse::of).toList();
    }
}
