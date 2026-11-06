package io.github.akarp0508.koifolio.instrument;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.instrument.InstrumentController.InstrumentResponse;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class InstrumentTools {

    private final InstrumentService instruments;

    InstrumentTools(InstrumentService instruments) {
        this.instruments = instruments;
    }

    @McpTool(name = "get_company", description = "What the user keeps on one company: sector, risk, thesis, Markdown analysis and tags.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    InstrumentResponse getCompany(McpTransportContext context, @McpToolParam(description = "Ticker") String ticker) {
        UUID userId = AgentCaller.of(context).userId();
        String normalized = InstrumentService.normalizeTicker(ticker);
        return instruments.views(userId).values().stream()
                .filter(view -> view.ticker().equals(normalized))
                .findFirst()
                .map(view -> InstrumentResponse.of(instruments.get(userId, view.id())))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "instrument_not_found", "No company with that ticker"));
    }
}
