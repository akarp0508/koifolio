package io.github.akarp0508.koifolio.instrument;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.RequestChecker;
import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.agent.AgentScope;
import io.github.akarp0508.koifolio.instrument.InstrumentController.InstrumentResponse;
import io.github.akarp0508.koifolio.instrument.InstrumentController.UpdateInstrumentRequest;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class InstrumentTools {

    private final InstrumentService instruments;

    private final RequestChecker checker;

    InstrumentTools(InstrumentService instruments, RequestChecker checker) {
        this.instruments = instruments;
        this.checker = checker;
    }

    private Optional<InstrumentService.InstrumentView> find(UUID userId, String ticker) {
        String normalized = InstrumentService.normalizeTicker(ticker);
        return instruments.views(userId).values().stream().filter(view -> view.ticker().equals(normalized)).findFirst();
    }

    @McpTool(name = "get_company", description = "What the user keeps on one company: sector, risk, thesis, Markdown analysis and tags.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    InstrumentResponse getCompany(McpTransportContext context, @McpToolParam(description = "Ticker") String ticker) {
        UUID userId = AgentCaller.of(context).userId();
        return find(userId, ticker)
                .map(view -> InstrumentResponse.of(instruments.get(userId, view.id())))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "instrument_not_found", "No company with that ticker"));
    }

    @McpTool(name = "update_company", description = """
            Updates what the user keeps on a company, adding it if it's new. Only the fields you send change. The analysis \
            and tags are replaced as a whole, so read them with get_company first. It applies at once and goes in the change log.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    InstrumentResponse updateCompany(McpTransportContext context,
                                     @McpToolParam(description = "Ticker") String ticker,
                                     @McpToolParam(required = false, description = "Company name") String name,
                                     @McpToolParam(required = false, description = "Sector") String sector,
                                     @McpToolParam(required = false, description = "1 (very low) to 5 (very high)") Integer risk,
                                     @McpToolParam(required = false, description = "Why it's held, up to 1000 characters") String thesis,
                                     @McpToolParam(required = false, description = "Markdown analysis") String analysis,
                                     @McpToolParam(required = false, description = "Tags") List<String> tags) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.ANALYSIS_WRITE);
        UUID userId = caller.userId();
        Optional<InstrumentService.InstrumentView> existing = find(userId, ticker);
        InstrumentDetails now = existing.map(InstrumentService.InstrumentView::details)
                .orElse(new InstrumentDetails(null, null, null, null, null, null));
        UpdateInstrumentRequest request = new UpdateInstrumentRequest(name == null ? now.name() : name,
                sector == null ? now.sector() : sector, risk == null ? now.risk() : risk,
                thesis == null ? now.thesis() : thesis, analysis == null ? now.analysis() : analysis,
                tags == null ? List.copyOf(now.tags()) : tags);
        checker.check(request);
        return InstrumentResponse.of(existing
                .map(view -> instruments.update(userId, view.id(), request.details(), caller.actor()))
                .orElseGet(() -> instruments.create(userId, ticker, request.details(), caller.actor())));
    }
}
