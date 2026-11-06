package io.github.akarp0508.koifolio.account;

import io.github.akarp0508.koifolio.account.AccountController.AccountResponse;
import io.github.akarp0508.koifolio.agent.AgentCaller;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
class AccountTools {

    private final AccountService accounts;

    AccountTools(AccountService accounts) {
        this.accounts = accounts;
    }

    @McpTool(name = "list_accounts", description = "The user's accounts, with their descriptions, currencies, cash balances and tax rates.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<AccountResponse> listAccounts(McpTransportContext context) {
        return accounts.list(AgentCaller.of(context).userId()).stream().map(AccountResponse::of).toList();
    }
}
