package io.github.akarp0508.koifolio.dashboard;

import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.dashboard.DashboardController.DashboardResponse;
import io.github.akarp0508.koifolio.dashboard.DashboardController.PortfolioHistoryResponse;
import io.modelcontextprotocol.common.McpTransportContext;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class DashboardTools {

    private final DashboardService dashboards;

    DashboardTools(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    private static DashboardService.Filter filter(UUID account) {
        return new DashboardService.Filter(account, null, Set.of(), Set.of());
    }

    @McpTool(name = "get_portfolio", description = """
            Totals in the user's base currency, with gains, dividends, cash, sector and risk breakdowns, what needs \
            attention and holdings by company. Weights are of the given account, or of everything without one.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    DashboardResponse getPortfolio(McpTransportContext context,
                                   @McpToolParam(required = false, description = "Account id from list_accounts") UUID account) {
        return DashboardResponse.of(dashboards.build(AgentCaller.of(context).userId(), filter(account)));
    }

    @McpTool(name = "get_history", description = """
            Portfolio value, cost and gain per day in the base currency, plus changes over 1D to 1Y, with positions, bonds \
            and cash. Cost includes that cash. The gain includes realized gains and dividends. If pricesLoading is above 0, \
            older prices are still being fetched and those positions show at trade prices for now. There can be thousands of points, so ask for only the dates you need.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    PortfolioHistoryResponse getHistory(McpTransportContext context,
                                        @McpToolParam(required = false, description = "Account id from list_accounts") UUID account,
                                        @McpToolParam(required = false, description = "First day, as YYYY-MM-DD") LocalDate from,
                                        @McpToolParam(required = false, description = "Last day, as YYYY-MM-DD") LocalDate to) {
        var history = PortfolioHistoryResponse.of(dashboards.history(AgentCaller.of(context).userId(), filter(account)));
        return new PortfolioHistoryResponse(history.currency(), history.points().stream()
                .filter(point -> (from == null || !point.date().isBefore(from)) && (to == null || !point.date().isAfter(to)))
                .toList(), history.changes(), history.pricesLoading());
    }
}
