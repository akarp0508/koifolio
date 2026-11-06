package io.github.akarp0508.koifolio.transaction;

import io.github.akarp0508.koifolio.RequestChecker;
import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.agent.AgentScope;
import io.github.akarp0508.koifolio.agent.ToolArguments;
import io.github.akarp0508.koifolio.review.ReviewService;
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

    private final RequestChecker checker;

    private final ReviewService review;

    TransactionTools(TransactionService transactions, RequestChecker checker, ReviewService review) {
        this.transactions = transactions;
        this.checker = checker;
        this.review = review;
    }

    @McpTool(name = "add_transaction", description = """
            Adds a transaction. It counts in every number straight away, but stays marked unvalidated until the user \
            approves or rejects it. Check list_transactions first so nothing is entered twice. Amounts are in the account's \
            currency. Numbers are sent as text, like "12.50". buy and sell need ticker, quantity and price. dividend needs ticker and gross amount, and should have \
            withheldTax. deposit, withdrawal and fee need amount. split needs ticker, splitFrom and splitTo.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    TransactionResponse addTransaction(McpTransportContext context,
                                       @McpToolParam(description = "Account id from list_accounts") UUID account,
                                       @McpToolParam(description = "One of deposit, withdrawal, buy, sell, dividend, fee, split") String type,
                                       @McpToolParam(description = "As YYYY-MM-DD") LocalDate date,
                                       @McpToolParam(description = """
                                               Whether this moves the account's cash balance. Use false for history from \
                                               before the account's balanceSetOn, which the balance already includes.""") Boolean affectsBalance,
                                       @McpToolParam(required = false, description = "Ticker") String ticker,
                                       @McpToolParam(required = false, description = "Shares") String quantity,
                                       @McpToolParam(required = false, description = "Per share") String price,
                                       @McpToolParam(required = false, description = "Broker fee") String fee,
                                       @McpToolParam(required = false, description = "Cash amount, gross for a dividend") String amount,
                                       @McpToolParam(required = false, description = "Tax withheld from a dividend") String withheldTax,
                                       @McpToolParam(required = false, description = "Old shares in a split") String splitFrom,
                                       @McpToolParam(required = false, description = "New shares for that many old ones") String splitTo,
                                       @McpToolParam(required = false, description = """
                                               Go ahead if this takes the balance below zero. Otherwise it's refused, so \
                                               ask the user first.""") Boolean allowNegativeBalance) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.TRANSACTIONS_WRITE);
        TransactionRequest request = new TransactionRequest(account, ToolArguments.choice(TransactionType.class, "type", type), date,
                ticker, ToolArguments.decimal("quantity", quantity), ToolArguments.decimal("price", price),
                ToolArguments.decimal("fee", fee), ToolArguments.decimal("amount", amount),
                ToolArguments.decimal("withheldTax", withheldTax), ToolArguments.decimal("splitFrom", splitFrom),
                ToolArguments.decimal("splitTo", splitTo), affectsBalance, allowNegativeBalance);
        checker.check(request);
        return TransactionResponse.of(transactions.create(caller.userId(), request, caller.actor()));
    }

    @McpTool(name = "update_transaction", description = """
            Proposes changes to a transaction. Give only the fields to change, and the rest stay as they are. Nothing changes \
            until the user approves it. Check list_pending_changes first so it isn't proposed twice. Amounts are in the \
            account's currency. Numbers are sent as text, like "12.50".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    ReviewService.Proposed updateTransaction(McpTransportContext context,
                                             @McpToolParam(description = "Transaction id from list_transactions") UUID id,
                                             @McpToolParam(required = false, description = "One of deposit, withdrawal, buy, sell, dividend, fee, split") String type,
                                             @McpToolParam(required = false, description = "As YYYY-MM-DD") LocalDate date,
                                             @McpToolParam(required = false, description = "Ticker") String ticker,
                                             @McpToolParam(required = false, description = "Shares") String quantity,
                                             @McpToolParam(required = false, description = "Per share") String price,
                                             @McpToolParam(required = false, description = "Broker fee") String fee,
                                             @McpToolParam(required = false, description = "Cash amount, gross for a dividend") String amount,
                                             @McpToolParam(required = false, description = "Tax withheld from a dividend") String withheldTax,
                                             @McpToolParam(required = false, description = "Old shares in a split") String splitFrom,
                                             @McpToolParam(required = false, description = "New shares for that many old ones") String splitTo,
                                             @McpToolParam(required = false, description = "Whether this moves the account's cash balance") Boolean affectsBalance) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.TRANSACTIONS_WRITE);
        return review.proposeTransactionUpdate(caller.userId(), caller.name(), id, new TransactionService.Edit(
                type == null ? null : ToolArguments.choice(TransactionType.class, "type", type), date, ticker,
                ToolArguments.decimal("quantity", quantity), ToolArguments.decimal("price", price), ToolArguments.decimal("fee", fee),
                ToolArguments.decimal("amount", amount), ToolArguments.decimal("withheldTax", withheldTax),
                ToolArguments.decimal("splitFrom", splitFrom), ToolArguments.decimal("splitTo", splitTo), affectsBalance));
    }

    @McpTool(name = "delete_transaction", description = """
            Proposes deleting a transaction. Nothing changes until the user approves it. Use it for a transaction that's \
            already approved: one still waiting for review can simply be left for the user to reject.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    ReviewService.Proposed deleteTransaction(McpTransportContext context,
                                             @McpToolParam(description = "Transaction id from list_transactions") UUID id) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.TRANSACTIONS_WRITE);
        return review.proposeTransactionDelete(caller.userId(), caller.name(), id);
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
