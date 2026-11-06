package io.github.akarp0508.koifolio.bond;

import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.agent.AgentScope;
import io.github.akarp0508.koifolio.agent.ToolArguments;
import io.github.akarp0508.koifolio.bond.BondController.BondResponse;
import io.github.akarp0508.koifolio.review.ReviewService;
import io.modelcontextprotocol.common.McpTransportContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class BondTools {

    private final BondService bonds;
    private final ReviewService review;

    BondTools(BondService bonds, ReviewService review) {
        this.bonds = bonds;
        this.review = review;
    }

    @McpTool(name = "list_bonds", description = "The user's Polish retail bonds, with today's accrued value and what early redemption would pay before tax, in PLN.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<BondResponse> listBonds(McpTransportContext context) {
        return bonds.list(AgentCaller.of(context).userId()).stream().map(BondResponse::of).toList();
    }

    @McpTool(name = "add_bond", description = """
            Proposes a bond purchase. Nothing changes until the user approves it. Only TOS, ROS, EDO and ROD are supported, \\
            and only in a PLN account. Check list_bonds and list_pending_changes first so it isn't proposed twice. Never guess \\
            a rate: take it from the bond's confirmation or the issue letter, and ask the user if unsure.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    ReviewService.Proposed addBond(McpTransportContext context,
                                   @McpToolParam(description = "Series, like TOS0828") String series,
                                   @McpToolParam(description = "Account id from list_accounts") UUID account,
                                   @McpToolParam(description = "Number of bonds, each 100 PLN") Integer quantity,
                                   @McpToolParam(description = "As YYYY-MM-DD") LocalDate purchaseDate,
                                   @McpToolParam(description = """
                                           In percent, as text like "5.40". One for a TOS, each year's so far for the others""") List<String> rates,
                                   @McpToolParam(description = """
                                           Whether the purchase came out of the account's cash in koifolio. Use false if the \\
                                           balance doesn't include this money""") Boolean affectsBalance,
                                   @McpToolParam(required = false, description = "Early redemption fee per bond. Only a TOS can leave it out") String earlyFee) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.TRANSACTIONS_WRITE);
        return review.proposeBond(caller.userId(), caller.name(), new BondService.Input(series.strip().toUpperCase(java.util.Locale.ROOT),
                account, quantity, purchaseDate, rates.stream().map(rate -> ToolArguments.decimal("rates", rate)).toList(),
                ToolArguments.decimal("earlyFee", earlyFee), affectsBalance));
    }
}
