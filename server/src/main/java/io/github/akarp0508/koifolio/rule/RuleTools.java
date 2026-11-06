package io.github.akarp0508.koifolio.rule;

import io.github.akarp0508.koifolio.RequestChecker;
import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.agent.AgentScope;
import io.github.akarp0508.koifolio.agent.ToolArguments;
import io.github.akarp0508.koifolio.domain.RuleType;
import io.github.akarp0508.koifolio.rule.RuleController.RuleCheckResponse;
import io.github.akarp0508.koifolio.rule.RuleController.RuleRequest;
import io.github.akarp0508.koifolio.review.ReviewService;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class RuleTools {

    private final RuleService rules;

    private final RequestChecker checker;
    private final ReviewService review;

    RuleTools(RuleService rules, RequestChecker checker, ReviewService review) {
        this.rules = rules;
        this.checker = checker;
        this.review = review;
    }

    @McpTool(name = "save_rule", description = """
            Proposes a new rule, or replacing one when given its id from check_rules. Nothing changes until the user approves it.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    ReviewService.Proposed saveRule(McpTransportContext context,
                          @McpToolParam(description = "max_position_weight or min_low_risk_weight") String type,
                          @McpToolParam(description = "The limit in percent, as text like \"20\"") String limitPercent,
                          @McpToolParam(required = false, description = "The rule to replace") UUID id,
                          @McpToolParam(required = false, description = "Account id from list_accounts. Everything without one") UUID account,
                          @McpToolParam(required = false, description = "min_low_risk_weight only: the highest risk that counts as low") Integer maxRisk) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.RULES_WRITE);
        RuleRequest request = new RuleRequest(ToolArguments.choice(RuleType.class, "type", type), account,
                ToolArguments.decimal("limitPercent", limitPercent), maxRisk);
        checker.check(request);
        return review.proposeRule(caller.userId(), caller.name(), id, request.input());
    }

    @McpTool(name = "delete_rule", description = "Proposes removing a rule, by its id from check_rules. Nothing changes until the user approves it.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    ReviewService.Proposed deleteRule(McpTransportContext context, @McpToolParam(description = "The rule to remove") UUID id) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.RULES_WRITE);
        return review.proposeRuleDelete(caller.userId(), caller.name(), id);
    }

    @McpTool(name = "check_rules", description = "The user's rules, each with whether it passes now and what's over the limit. They're indicators and aren't enforced.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<RuleCheckResponse> checkRules(McpTransportContext context) {
        return rules.check(AgentCaller.of(context).userId()).stream().map(RuleCheckResponse::of).toList();
    }
}
