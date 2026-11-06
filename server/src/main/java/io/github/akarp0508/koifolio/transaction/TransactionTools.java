package io.github.akarp0508.koifolio.transaction;

import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.transaction.TransactionController.TransactionResponse;
import io.modelcontextprotocol.common.McpTransportContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class TransactionTools {

    private final TransactionService transactions;

    TransactionTools(TransactionService transactions) {
        this.transactions = transactions;
    }

    @McpTool(name = "list_transactions", description = "The user's transactions, newest first, in each account's currency.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<TransactionResponse> listTransactions(McpTransportContext context,
                                               @McpToolParam(required = false, description = "Account id from list_accounts") UUID account,
                                               @McpToolParam(required = false, description = "First day, as YYYY-MM-DD") LocalDate from,
                                               @McpToolParam(required = false, description = "Last day, as YYYY-MM-DD") LocalDate to) {
        return transactions.list(AgentCaller.of(context).userId(), account).stream()
                .map(TransactionResponse::of)
                .filter(found -> (from == null || !found.date().isBefore(from)) && (to == null || !found.date().isAfter(to)))
                .toList();
    }
}
