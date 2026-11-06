package io.github.akarp0508.koifolio.tax;

import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.tax.TaxController.TaxResponse;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class TaxTools {

    private final TaxService tax;

    TaxTools(TaxService tax) {
        this.tax = tax;
    }

    @McpTool(name = "get_tax", description = "An estimate of tax on sales and dividends for one year, in the base currency. Not tax advice.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    TaxResponse getTax(McpTransportContext context,
                       @McpToolParam(required = false, description = "Calendar year, this year without one") Integer year) {
        return TaxResponse.of(tax.estimate(AgentCaller.of(context).userId(), year));
    }
}
