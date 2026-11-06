package io.github.akarp0508.koifolio.transaction;

import io.github.akarp0508.koifolio.RequestChecker;
import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.agent.AgentScope;
import io.github.akarp0508.koifolio.agent.ToolArguments;
import io.github.akarp0508.koifolio.review.ReviewService;
import io.github.akarp0508.koifolio.transaction.TransactionController.TransactionResponse;
import io.modelcontextprotocol.common.McpTransportContext;
import io.swagger.v3.oas.annotations.media.Schema;
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
            withheldTax. interest is what a broker or bank pays on cash, so not a deposit: it needs the gross amount, and \
            should have withheldTax. deposit, withdrawal and fee need amount. split needs ticker, splitFrom and splitTo. spinoff is for shares of a \
            new company received from one already held, with no cash: ticker is the new company, and it needs parentTicker, \
            quantity and costShare, the percent of the parent's cost that moves across.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    TransactionResponse addTransaction(McpTransportContext context,
                                       @McpToolParam(description = "Account id from list_accounts") UUID account,
                                       @McpToolParam(description = "One of deposit, withdrawal, buy, sell, dividend, interest, fee, split, spinoff") String type,
                                       @McpToolParam(description = "As YYYY-MM-DD") LocalDate date,
                                       @McpToolParam(description = """
                                               Whether this moves the account's cash balance. Use false for history from \
                                               before the account's balanceSetOn, which the balance already includes.""") Boolean affectsBalance,
                                       @McpToolParam(required = false, description = "Ticker") String ticker,
                                       @McpToolParam(required = false, description = "Shares") String quantity,
                                       @McpToolParam(required = false, description = "Per share") String price,
                                       @McpToolParam(required = false, description = "Broker fee") String fee,
                                       @McpToolParam(required = false, description = "Cash amount, gross for a dividend or interest") String amount,
                                       @McpToolParam(required = false, description = "Tax withheld from a dividend or interest") String withheldTax,
                                       @McpToolParam(required = false, description = "Old shares in a split") String splitFrom,
                                       @McpToolParam(required = false, description = "New shares for that many old ones") String splitTo,
                                       @McpToolParam(required = false, description = "The company a spin-off came from") String parentTicker,
                                       @McpToolParam(required = false, description = "Percent of the parent's cost a spin-off moves") String costShare,
                                       @McpToolParam(required = false, description = "Why, in the user's words, if they gave a reason") String note,
                                       @McpToolParam(required = false, description = """
                                               Go ahead if this takes the balance below zero. Otherwise it's refused, so \
                                               ask the user first.""") Boolean allowNegativeBalance) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.TRANSACTIONS_WRITE);
        TransactionRequest request = new TransactionRequest(account, ToolArguments.choice(TransactionType.class, "type", type), date,
                ticker, ToolArguments.decimal("quantity", quantity), ToolArguments.decimal("price", price),
                ToolArguments.decimal("fee", fee), ToolArguments.decimal("amount", amount),
                ToolArguments.decimal("withheldTax", withheldTax), ToolArguments.decimal("splitFrom", splitFrom),
                ToolArguments.decimal("splitTo", splitTo), parentTicker, ToolArguments.decimal("costShare", costShare), note,
                affectsBalance, allowNegativeBalance);
        checker.check(request);
        return TransactionResponse.of(transactions.create(caller.userId(), request, caller.actor()));
    }

    /** One row of add_transactions, with the same fields as add_transaction. */
    record ImportRowArguments(UUID account, String type, LocalDate date, Boolean affectsBalance,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String ticker,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String quantity,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String price,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String fee,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String amount,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String withheldTax,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String splitFrom,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String splitTo,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String parentTicker,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String costShare,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String note,
                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean allowNegativeBalance) {
    }

    /** @param status added, valid or refused. A valid row wasn't added, because another one was refused */
    record ImportRowResponse(int row, String status, TransactionResponse transaction, String error) {
    }

    /** @param importId names this import on the Review page, left out if nothing was added */
    record ImportResponse(boolean added, UUID importId, List<ImportRowResponse> rows) {
    }

    @McpTool(name = "add_transactions", description = """
            Adds up to 1000 transactions in one go, for a broker history for example. All of them are added or none are: \
            every row is checked, and if any is refused nothing changes, and rows says what to fix. Otherwise they work like \
            add_transaction, and the user can review them together as one import. Each row has the fields of add_transaction, \
            and rows apply in order, so put them oldest first.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    ImportResponse addTransactions(McpTransportContext context,
                                   @McpToolParam(description = "The transactions, oldest first") List<ImportRowArguments> transactions) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.TRANSACTIONS_WRITE);
        TransactionService.Imported imported = this.transactions.createAll(caller.userId(),
                transactions.stream().map(TransactionTools::request).toList(), caller.actor());
        return new ImportResponse(imported.importId() != null, imported.importId(), imported.rows().stream()
                .map(row -> new ImportRowResponse(row.row(), row.error() != null ? "refused" : row.added() != null ? "added" : "valid",
                        row.added() == null ? null : TransactionResponse.of(row.added()), row.error()))
                .toList());
    }

    private static TransactionRequest request(ImportRowArguments row) {
        return new TransactionRequest(row.account(), ToolArguments.choice(TransactionType.class, "type", row.type()), row.date(),
                row.ticker(), ToolArguments.decimal("quantity", row.quantity()), ToolArguments.decimal("price", row.price()),
                ToolArguments.decimal("fee", row.fee()), ToolArguments.decimal("amount", row.amount()),
                ToolArguments.decimal("withheldTax", row.withheldTax()), ToolArguments.decimal("splitFrom", row.splitFrom()),
                ToolArguments.decimal("splitTo", row.splitTo()), row.parentTicker(),
                ToolArguments.decimal("costShare", row.costShare()), row.note(), row.affectsBalance(), row.allowNegativeBalance());
    }

    @McpTool(name = "update_transaction", description = """
            Proposes changes to a transaction. Give only the fields to change, and the rest stay as they are. Nothing changes \
            until the user approves it. Check list_pending_changes first so it isn't proposed twice. Amounts are in the \
            account's currency. Numbers are sent as text, like "12.50".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    ReviewService.Proposed updateTransaction(McpTransportContext context,
                                             @McpToolParam(description = "Transaction id from list_transactions") UUID id,
                                             @McpToolParam(required = false, description = "One of deposit, withdrawal, buy, sell, dividend, interest, fee, split, spinoff") String type,
                                             @McpToolParam(required = false, description = "As YYYY-MM-DD") LocalDate date,
                                             @McpToolParam(required = false, description = "Ticker") String ticker,
                                             @McpToolParam(required = false, description = "Shares") String quantity,
                                             @McpToolParam(required = false, description = "Per share") String price,
                                             @McpToolParam(required = false, description = "Broker fee") String fee,
                                             @McpToolParam(required = false, description = "Cash amount, gross for a dividend or interest") String amount,
                                             @McpToolParam(required = false, description = "Tax withheld from a dividend or interest") String withheldTax,
                                             @McpToolParam(required = false, description = "Old shares in a split") String splitFrom,
                                             @McpToolParam(required = false, description = "New shares for that many old ones") String splitTo,
                                             @McpToolParam(required = false, description = "The company a spin-off came from") String parentTicker,
                                             @McpToolParam(required = false, description = "Percent of the parent's cost a spin-off moves") String costShare,
                                             @McpToolParam(required = false, description = "Why, in the user's words. Empty text removes it") String note,
                                             @McpToolParam(required = false, description = "Whether this moves the account's cash balance") Boolean affectsBalance) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.TRANSACTIONS_WRITE);
        return review.proposeTransactionUpdate(caller.userId(), caller.name(), id, new TransactionService.Edit(
                type == null ? null : ToolArguments.choice(TransactionType.class, "type", type), date, ticker,
                ToolArguments.decimal("quantity", quantity), ToolArguments.decimal("price", price), ToolArguments.decimal("fee", fee),
                ToolArguments.decimal("amount", amount), ToolArguments.decimal("withheldTax", withheldTax),
                ToolArguments.decimal("splitFrom", splitFrom), ToolArguments.decimal("splitTo", splitTo), parentTicker,
                ToolArguments.decimal("costShare", costShare), note, affectsBalance));
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
