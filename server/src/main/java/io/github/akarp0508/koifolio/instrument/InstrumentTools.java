package io.github.akarp0508.koifolio.instrument;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.RequestChecker;
import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.agent.AgentScope;
import io.github.akarp0508.koifolio.instrument.InstrumentController.InstrumentResponse;
import io.github.akarp0508.koifolio.instrument.InstrumentController.UpdateInstrumentRequest;
import io.github.akarp0508.koifolio.review.ReviewService;
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
    private final ReviewService review;

    InstrumentTools(InstrumentService instruments, RequestChecker checker, ReviewService review) {
        this.instruments = instruments;
        this.checker = checker;
        this.review = review;
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
            Proposes changes to what the user keeps on a company, adding it if it's new. Nothing changes until the user \
            approves it, so get_company keeps showing the old values until then. Only the fields you send change. The \
            analysis and tags are replaced as a whole, so read them with get_company first.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    ReviewService.Proposed updateCompany(McpTransportContext context,
                                     @McpToolParam(description = "Ticker") String ticker,
                                     @McpToolParam(required = false, description = "Company name") String name,
                                     @McpToolParam(required = false, description = "Sector") String sector,
                                     @McpToolParam(required = false, description = "1 (very low) to 5 (very high)") Integer risk,
                                     @McpToolParam(required = false, description = "Why it's held, up to 1000 characters") String thesis,
                                     @McpToolParam(required = false, description = "Markdown analysis") String analysis,
                                     @McpToolParam(required = false, description = "Tags") List<String> tags) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.ANALYSIS_WRITE);
        checker.check(new UpdateInstrumentRequest(name, sector, risk, thesis, analysis, tags));
        if (name == null && sector == null && risk == null && thesis == null && analysis == null && tags == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "nothing_to_change", "Send at least one field to change");
        }
        return review.proposeCompany(caller.userId(), caller.name(), ticker, new InstrumentService.Edit(name, sector, risk, thesis,
                analysis, tags == null ? null : Tags.clean(tags)));
    }
}
