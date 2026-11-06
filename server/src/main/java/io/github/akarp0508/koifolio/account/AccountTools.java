package io.github.akarp0508.koifolio.account;

import io.github.akarp0508.koifolio.RequestChecker;
import io.github.akarp0508.koifolio.account.AccountController.AccountResponse;
import io.github.akarp0508.koifolio.account.AccountController.CreateAccountRequest;
import io.github.akarp0508.koifolio.account.AccountController.UpdateAccountRequest;
import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.github.akarp0508.koifolio.agent.AgentScope;
import io.github.akarp0508.koifolio.agent.ToolArguments;
import io.github.akarp0508.koifolio.domain.CostMethod;
import io.github.akarp0508.koifolio.review.ReviewService;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
class AccountTools {

    private final AccountService accounts;
    private final RequestChecker checker;
    private final ReviewService review;

    AccountTools(AccountService accounts, RequestChecker checker, ReviewService review) {
        this.accounts = accounts;
        this.checker = checker;
        this.review = review;
    }

    @McpTool(name = "list_accounts", description = "The user's accounts, with their descriptions, currencies, cash balances and tax rates.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<AccountResponse> listAccounts(McpTransportContext context) {
        return accounts.list(AgentCaller.of(context).userId()).stream().map(AccountResponse::of).toList();
    }

    @McpTool(name = "create_account", description = """
            Proposes a new account. Nothing changes until the user approves it, so it isn't in list_accounts and can't take \
            transactions until then. Check list_accounts and list_pending_changes first so it isn't proposed twice. Numbers \
            are sent as text, like "12.50".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    ReviewService.Proposed createAccount(McpTransportContext context,
                                         @McpToolParam(description = "Account name") String name,
                                         @McpToolParam(description = "3-letter code like PLN. It can't be changed later") String currency,
                                         @McpToolParam(required = false, description = "Cash in the account now. 0 without one") String balance,
                                         @McpToolParam(required = false, description = "Notes on the account") String description,
                                         @McpToolParam(required = false, description = "average or fifo. average without one") String costMethod,
                                         @McpToolParam(required = false, description = """
                                                 Tax rate in percent, like "19". Without one the account stays out of tax estimates""") String taxRate) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.ACCOUNTS_WRITE);
        CreateAccountRequest request = new CreateAccountRequest(name, description, currency.strip().toUpperCase(Locale.ROOT),
                ToolArguments.decimal("balance", balance),
                costMethod == null ? null : ToolArguments.choice(CostMethod.class, "costMethod", costMethod),
                ToolArguments.decimal("taxRate", taxRate));
        checker.check(request);
        return review.proposeAccount(caller.userId(), caller.name(), request.input());
    }

    @McpTool(name = "update_account", description = """
            Proposes changes to an account. Give only the fields to change. Nothing changes until the user approves it, and \
            the currency can't be changed. Numbers are sent as text, like "12.50".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    ReviewService.Proposed updateAccount(McpTransportContext context,
                                         @McpToolParam(description = "Account id from list_accounts") UUID id,
                                         @McpToolParam(required = false, description = "Account name") String name,
                                         @McpToolParam(required = false, description = "Notes on the account") String description,
                                         @McpToolParam(required = false, description = "Cash in the account now") String balance,
                                         @McpToolParam(required = false, description = "average or fifo") String costMethod,
                                         @McpToolParam(required = false, description = "Tax rate in percent, like \"19\"") String taxRate,
                                         @McpToolParam(required = false, description = "Leave the account out of tax estimates") Boolean clearTaxRate) {
        AgentCaller caller = AgentCaller.of(context).require(AgentScope.ACCOUNTS_WRITE);
        UpdateAccountRequest request = new UpdateAccountRequest(name, description, ToolArguments.decimal("balance", balance),
                costMethod == null ? null : ToolArguments.choice(CostMethod.class, "costMethod", costMethod),
                ToolArguments.decimal("taxRate", taxRate), clearTaxRate);
        checker.check(request);
        return review.proposeAccountUpdate(caller.userId(), caller.name(), id, new AccountService.Edit(request.name(),
                request.description(), request.balance(), request.costMethod(), request.taxRate(), request.clearTaxRate()));
    }
}
