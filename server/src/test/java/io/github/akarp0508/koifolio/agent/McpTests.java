package io.github.akarp0508.koifolio.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabase.class)
class McpTests {

    private static final List<String> READ_TOOLS = List.of("list_accounts", "get_portfolio", "get_history", "get_positions",
            "get_plan", "get_company", "list_transactions", "check_rules", "get_tax", "list_pending_changes");
    private static final List<String> WRITE_TOOLS = List.of("add_transaction", "update_company", "save_rule", "delete_rule");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private String anna;
    private String bob;
    private String account;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
        String body = as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Main\",\"currency\":\"PLN\",\"balance\":\"500\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        account = JsonPath.read(body, "$.id");
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountId":"%s","type":"buy","date":"2026-01-05","ticker":"PAS","quantity":"2","price":"10","affectsBalance":false}"""
                .formatted(account))).andExpect(status().isCreated());
    }

    private String tokenFor(String username) throws Exception {
        String credentials = "{\"username\":\"%s\",\"password\":\"correct horse\"}".formatted(username);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String createToken(String jwt, String name) throws Exception {
        String body = as(jwt, post("/api/v1/agent-tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.secret");
    }

    private ResultActions mcp(String token, String method, String params) throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"%s\",\"params\":%s}".formatted(method, params);
        return as(token, post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(json));
    }

    private String call(String token, String tool, String arguments) throws Exception {
        String body = mcp(token, "tools/call", "{\"name\":\"%s\",\"arguments\":%s}".formatted(tool, arguments))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(body, "$.result.isError")).as(body).isFalse();
        return JsonPath.read(body, "$.result.content[0].text");
    }

    @Test
    void refusesARequestWithoutAToken() throws Exception {
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAWrongToken() throws Exception {
        mcp("kf_wrong", "tools/list", "{}").andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAWebsiteLogin() throws Exception {
        mcp(anna, "tools/list", "{}").andExpect(status().isUnauthorized());
    }

    @Test
    void anAgentTokenDoesntWorkOnTheWebsiteApi() throws Exception {
        as(createToken(anna, "Claude"), get("/api/v1/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsEveryToolAndMarksWhichOnlyRead() throws Exception {
        String body = mcp(createToken(anna, "Claude"), "tools/list", "{}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.result.tools[*].name"))
                .containsExactlyInAnyOrderElementsOf(Stream.concat(READ_TOOLS.stream(), WRITE_TOOLS.stream()).toList());
        assertThat(JsonPath.<List<String>>read(body, "$.result.tools[?(@.annotations.readOnlyHint == false)].name"))
                .containsExactlyInAnyOrderElementsOf(WRITE_TOOLS);
    }

    @Test
    void readToolsReturnTheSiteNumbers() throws Exception {
        String token = createToken(anna, "Claude");

        assertThat(JsonPath.<String>read(call(token, "list_accounts", "{}"), "$[0].name")).isEqualTo("Main");
        assertThat(JsonPath.<String>read(call(token, "get_portfolio", "{\"account\":\"%s\"}".formatted(account)), "$.cash"))
                .isEqualTo("500.00");
        assertThat(JsonPath.<String>read(call(token, "get_positions", "{}"), "$[0].cost")).isEqualTo("20.00");
        assertThat(JsonPath.<String>read(call(token, "get_plan", "{\"account\":\"%s\",\"ticker\":\"pas\"}".formatted(account)),
                "$.instrumentId")).isNotBlank();
        assertThat(JsonPath.<String>read(call(token, "get_company", "{\"ticker\":\"pas\"}"), "$.ticker")).isEqualTo("PAS");
        assertThat(JsonPath.<String>read(call(token, "list_transactions", "{}"), "$[0].ticker")).isEqualTo("PAS");
        assertThat(JsonPath.<Integer>read(call(token, "check_rules", "{}"), "$.length()")).isZero();
        assertThat(JsonPath.<String>read(call(token, "get_tax", "{}"), "$.totalTax")).isEqualTo("0.00");
        assertThat(JsonPath.<Integer>read(call(token, "get_history", "{}"), "$.points.length()")).isZero();
    }

    @Test
    void transactionsCanBeLimitedByDate() throws Exception {
        String token = createToken(anna, "Claude");

        assertThat(JsonPath.<Integer>read(call(token, "list_transactions", "{\"from\":\"2026-02-01\"}"), "$.length()")).isZero();
        assertThat(JsonPath.<Integer>read(call(token, "list_transactions", "{\"to\":\"2026-02-01\"}"), "$.length()")).isEqualTo(1);
    }

    @Test
    void anAgentOnlySeesItsOwnUser() throws Exception {
        assertThat(JsonPath.<Integer>read(call(createToken(bob, "Claude"), "list_accounts", "{}"), "$.length()")).isZero();
    }

    @Test
    void aMissingCompanyIsAToolError() throws Exception {
        mcp(createToken(anna, "Claude"), "tools/call", "{\"name\":\"get_company\",\"arguments\":{\"ticker\":\"NOPE\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    void theSecretIsntStoredAndIsShownOnce() throws Exception {
        String secret = createToken(anna, "Claude");

        assertThat(jdbc.sql("SELECT count(*) FROM agent_tokens WHERE token_hash = ?").param(secret).query(Long.class).single()).isZero();
        as(anna, get("/api/v1/agent-tokens"))
                .andExpect(jsonPath("$[0].name").value("Claude"))
                .andExpect(jsonPath("$[0].secret").doesNotExist())
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist());
    }

    @Test
    void useIsRecorded() throws Exception {
        String secret = createToken(anna, "Claude");
        as(anna, get("/api/v1/agent-tokens")).andExpect(jsonPath("$[0].lastUsedAt").doesNotExist());

        mcp(secret, "tools/list", "{}").andExpect(status().isOk());

        as(anna, get("/api/v1/agent-tokens")).andExpect(jsonPath("$[0].lastUsedAt").exists());
    }

    @Test
    void aRevokedTokenStopsWorking() throws Exception {
        String secret = createToken(anna, "Claude");
        String id = JsonPath.read(as(anna, get("/api/v1/agent-tokens")).andReturn().getResponse().getContentAsString(), "$[0].id");

        as(anna, delete("/api/v1/agent-tokens/" + id)).andExpect(status().isNoContent());

        mcp(secret, "tools/list", "{}").andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenStopsWorkingAfterItsLastDay() throws Exception {
        String secret = createToken(anna, "Claude");
        jdbc.sql("UPDATE agent_tokens SET expires_on = ?").param(java.sql.Date.valueOf(LocalDate.now().plusDays(1))).update();
        mcp(secret, "tools/list", "{}").andExpect(status().isOk());

        jdbc.sql("UPDATE agent_tokens SET expires_on = ?").param(java.sql.Date.valueOf(LocalDate.now().minusDays(2))).update();
        mcp(secret, "tools/list", "{}").andExpect(status().isUnauthorized());
    }

    @Test
    void namesAreUniquePerUser() throws Exception {
        createToken(anna, "Claude");
        createToken(bob, "Claude");

        as(anna, post("/api/v1/agent-tokens").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Claude \"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anotherUsersTokenCantBeRevoked() throws Exception {
        createToken(anna, "Claude");
        String id = JsonPath.read(as(anna, get("/api/v1/agent-tokens")).andReturn().getResponse().getContentAsString(), "$[0].id");

        as(bob, delete("/api/v1/agent-tokens/" + id)).andExpect(status().isNotFound());
    }

    private String tokenWith(String name, String scopes) throws Exception {
        String body = as(anna, post("/api/v1/agent-tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"scopes\":%s}".formatted(name, scopes)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.secret");
    }

    private String buy(String token, String ticker) throws Exception {
        return call(token, "add_transaction", """
                {"account":"%s","type":"buy","date":"2026-02-02","affectsBalance":true,"ticker":"%s","quantity":"3","price":"10.5"}"""
                .formatted(account, ticker));
    }

    private String balance() throws Exception {
        String body = as(anna, get("/api/v1/accounts")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].balance");
    }

    private String toolError(String token, String tool, String arguments) throws Exception {
        String body = mcp(token, "tools/call", "{\"name\":\"%s\",\"arguments\":%s}".formatted(tool, arguments))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.result.content[0].text");
    }

    @Test
    void anAgentTransactionCountsButWaitsForReview() throws Exception {
        String added = buy(createToken(anna, "Claude"), "ABC");

        assertThat(JsonPath.<String>read(added, "$.createdBy")).isEqualTo("Claude");
        assertThat(JsonPath.<Boolean>read(added, "$.unvalidated")).isTrue();
        assertThat(balance()).isEqualTo("468.50");
        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].ticker").value("ABC"));
        as(anna, get("/api/v1/dashboard")).andExpect(jsonPath("$.unvalidated").value(1));
        as(anna, get("/api/v1/transactions").param("unvalidated", "true"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticker").value("ABC"));
    }

    @Test
    void yourOwnTransactionsArentMarked() throws Exception {
        as(anna, get("/api/v1/transactions"))
                .andExpect(jsonPath("$[0].unvalidated").value(false))
                .andExpect(jsonPath("$[0].createdBy").doesNotExist());
    }

    @Test
    void approvingClearsTheMarkAndChangesNothingElse() throws Exception {
        String id = JsonPath.read(buy(createToken(anna, "Claude"), "ABC"), "$.id");

        as(anna, post("/api/v1/transactions/" + id + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unvalidated").value(false))
                .andExpect(jsonPath("$.createdBy").value("Claude"));

        assertThat(balance()).isEqualTo("468.50");
        as(anna, get("/api/v1/dashboard")).andExpect(jsonPath("$.unvalidated").value(0));
        as(anna, post("/api/v1/transactions/" + id + "/approve")).andExpect(status().isConflict());
    }

    @Test
    void rejectingUndoesItEverywhereButKeepsTheRow() throws Exception {
        String id = JsonPath.read(buy(createToken(anna, "Claude"), "ABC"), "$.id");

        as(anna, post("/api/v1/transactions/" + id + "/reject")).andExpect(status().isNoContent());

        assertThat(balance()).isEqualTo("500.00");
        as(anna, get("/api/v1/transactions/" + id)).andExpect(status().isNotFound());
        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[?(@.ticker == 'ABC')]").isEmpty());
        assertThat(jdbc.sql("SELECT count(*) FROM transactions WHERE id = ?::uuid AND deleted_at IS NOT NULL").param(id)
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void approveAllClearsEverythingWaiting() throws Exception {
        String token = createToken(anna, "Claude");
        buy(token, "ABC");
        buy(token, "DEF");

        as(anna, post("/api/v1/review/approve-all")).andExpect(jsonPath("$.transactions").value(2));

        as(anna, get("/api/v1/transactions").param("unvalidated", "true")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyAgentTransactionsCanBeRejected() throws Exception {
        String body = as(anna, get("/api/v1/transactions")).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$[0].id");

        as(anna, post("/api/v1/transactions/" + id + "/reject")).andExpect(status().isConflict());
    }

    @Test
    void anotherUserCantReviewYourTransactions() throws Exception {
        String id = JsonPath.read(buy(createToken(anna, "Claude"), "ABC"), "$.id");

        as(bob, post("/api/v1/transactions/" + id + "/approve")).andExpect(status().isNotFound());
        as(bob, post("/api/v1/transactions/" + id + "/reject")).andExpect(status().isNotFound());
    }

    @Test
    void aBadTransactionIsExplainedToTheAgent() throws Exception {
        String token = createToken(anna, "Claude");

        assertThat(toolError(token, "add_transaction", """
                {"account":"%s","type":"buy","date":"2026-02-02","affectsBalance":true,"ticker":"ABC"}""".formatted(account)))
                .contains("quantity");
        assertThat(toolError(token, "add_transaction", """
                {"account":"%s","type":"buy","date":"2026-02-02","affectsBalance":true,"ticker":"ABC","quantity":"1","price":"1.1234567"}"""
                .formatted(account))).contains("price");
        assertThat(toolError(token, "add_transaction", """
                {"account":"%s","type":"withdrawal","date":"2026-02-02","affectsBalance":true,"amount":"9000"}""".formatted(account)))
                .contains("Main", "-8500");
        assertThat(toolError(token, "add_transaction", """
                {"account":"%s","type":"gift","date":"2026-02-02","affectsBalance":true}""".formatted(account)))
                .contains("deposit, withdrawal, buy");
        assertThat(toolError(token, "add_transaction", """
                {"account":"%s","type":"deposit","date":"2026-02-02","affectsBalance":true,"amount":"lots"}""".formatted(account)))
                .contains("amount");
        assertThat(balance()).isEqualTo("500.00");
    }

    @Test
    void typesWorkInAnyCaseAndMoneyStaysExact() throws Exception {
        String added = call(createToken(anna, "Claude"), "add_transaction", """
                {"account":"%s","type":"DEPOSIT","date":"2026-02-02","affectsBalance":true,"amount":"0.1"}""".formatted(account));

        assertThat(JsonPath.<String>read(added, "$.type")).isEqualTo("deposit");
        assertThat(balance()).isEqualTo("500.10");
    }

    @Test
    void anAgentCantWriteToAnotherUsersAccount() throws Exception {
        assertThat(toolError(createToken(bob, "Claude"), "add_transaction", """
                {"account":"%s","type":"deposit","date":"2026-02-02","affectsBalance":true,"amount":"5"}""".formatted(account)))
                .contains("not found");
        assertThat(balance()).isEqualTo("500.00");
    }

    @Test
    void aTokenCanOnlyDoWhatItsAllowed() throws Exception {
        String readOnly = tokenWith("Reader", "[]");

        assertThat(toolError(readOnly, "add_transaction", """
                {"account":"%s","type":"deposit","date":"2026-02-02","affectsBalance":true,"amount":"5"}""".formatted(account)))
                .contains("isn't allowed");
        assertThat(toolError(readOnly, "update_company", "{\"ticker\":\"PAS\",\"risk\":2}")).contains("isn't allowed");
        assertThat(toolError(readOnly, "save_rule", "{\"type\":\"max_position_weight\",\"limitPercent\":\"20\"}"))
                .contains("isn't allowed");
        assertThat(JsonPath.<String>read(call(readOnly, "list_accounts", "{}"), "$[0].name")).isEqualTo("Main");
    }

    @Test
    void rulesNeedTheirOwnPermissionByDefault() throws Exception {
        String defaults = createToken(anna, "Claude");

        assertThat(toolError(defaults, "save_rule", "{\"type\":\"max_position_weight\",\"limitPercent\":\"20\"}"))
                .contains("isn't allowed");
        buy(defaults, "ABC");
        call(defaults, "update_company", "{\"ticker\":\"ABC\",\"risk\":2}");
    }

    @Test
    void updateCompanyChecksItsFields() throws Exception {
        assertThat(toolError(createToken(anna, "Claude"), "update_company", "{\"ticker\":\"PAS\",\"risk\":9}")).contains("risk");
    }

    @Test
    void theChangeLogNamesTheAgentAndKeepsBothSides() throws Exception {
        String token = createToken(anna, "Claude");
        String id = JsonPath.read(buy(token, "ABC"), "$.id");
        as(anna, post("/api/v1/transactions/" + id + "/approve")).andExpect(status().isOk());

        as(anna, get("/api/v1/audit-log"))
                .andExpect(jsonPath("$[0].action").value("approve"))
                .andExpect(jsonPath("$[0].actorType").value("USER"))
                .andExpect(jsonPath("$[0].actorName").doesNotExist())
                .andExpect(jsonPath("$[0].before.unvalidated").value(true))
                .andExpect(jsonPath("$[0].after.unvalidated").value(false))
                .andExpect(jsonPath("$[1].action").value("create"))
                .andExpect(jsonPath("$[1].actorType").value("AGENT"))
                .andExpect(jsonPath("$[1].actorName").value("Claude"))
                .andExpect(jsonPath("$[1].summary").value("Added buy ABC on 2026-02-02"))
                .andExpect(jsonPath("$[1].before").doesNotExist())
                .andExpect(jsonPath("$[1].after.ticker").value("ABC"));
    }

    @Test
    void yourOwnChangesAreLoggedToo() throws Exception {
        String body = as(anna, get("/api/v1/transactions")).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$[0].id");

        as(anna, delete("/api/v1/transactions/" + id)).andExpect(status().isNoContent());

        as(anna, get("/api/v1/audit-log"))
                .andExpect(jsonPath("$[0].action").value("delete"))
                .andExpect(jsonPath("$[0].before.ticker").value("PAS"))
                .andExpect(jsonPath("$[0].after").doesNotExist());
    }

    @Test
    void aRefusedChangeLeavesNoLogEntry() throws Exception {
        String before = as(anna, get("/api/v1/audit-log")).andReturn().getResponse().getContentAsString();

        toolError(createToken(anna, "Claude"), "add_transaction", """
                {"account":"%s","type":"withdrawal","date":"2026-02-02","affectsBalance":true,"amount":"9000"}""".formatted(account));

        as(anna, get("/api/v1/audit-log")).andExpect(jsonPath("$.length()").value(JsonPath.<Integer>read(before, "$.length()")));
    }

    @Test
    void theChangeLogIsPrivate() throws Exception {
        as(bob, get("/api/v1/audit-log")).andExpect(jsonPath("$.length()").value(0));
    }

    private void plan(String json) throws Exception {
        as(anna, put("/api/v1/positions/" + account + "/PAS/plan").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void accountChangesAreLogged() throws Exception {
        as(anna, patch("/api/v1/accounts/" + account).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"balance\":\"600\"}")).andExpect(status().isOk());

        as(anna, get("/api/v1/audit-log"))
                .andExpect(jsonPath("$[0].entityType").value("account"))
                .andExpect(jsonPath("$[0].action").value("update"))
                .andExpect(jsonPath("$[0].summary").value("Changed account Renamed"))
                .andExpect(jsonPath("$[0].before.name").value("Main"))
                .andExpect(jsonPath("$[0].before.balance").value(500))
                .andExpect(jsonPath("$[0].after.balance").value(600));
    }

    @Test
    void anEditThatChangesNothingIsntLogged() throws Exception {
        as(anna, patch("/api/v1/accounts/" + account).contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\":\"500.00\"}")).andExpect(status().isOk());

        as(anna, get("/api/v1/audit-log")).andExpect(jsonPath("$[0].entityType").value("transaction"));
    }

    @Test
    void deletingAnAccountKeepsEverythingThatWentWithIt() throws Exception {
        plan("{\"plan\":\"Hold\",\"targetPrice\":\"30\"}");
        as(anna, post("/api/v1/rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"max_position_weight\",\"accountId\":\"%s\",\"limitPercent\":\"20\"}".formatted(account)))
                .andExpect(status().isCreated());

        as(anna, delete("/api/v1/accounts/" + account)).andExpect(status().isNoContent());

        as(anna, get("/api/v1/audit-log"))
                .andExpect(jsonPath("$[0].summary").value("Deleted account Main with 1 transactions"))
                .andExpect(jsonPath("$[0].after").doesNotExist())
                .andExpect(jsonPath("$[0].before.transactions[0].ticker").value("PAS"))
                .andExpect(jsonPath("$[0].before.plans[0].plan").value("Hold"))
                .andExpect(jsonPath("$[0].before.rules[0].type").exists());
    }

    @Test
    void planChangesAreLogged() throws Exception {
        plan("{\"plan\":\"Hold\",\"targetPrice\":\"30\",\"tags\":[\"core\"]}");
        as(anna, get("/api/v1/audit-log"))
                .andExpect(jsonPath("$[0].entityType").value("plan"))
                .andExpect(jsonPath("$[0].action").value("create"))
                .andExpect(jsonPath("$[0].summary").value("Added plan for PAS in Main"))
                .andExpect(jsonPath("$[0].before").doesNotExist())
                .andExpect(jsonPath("$[0].after.plan").value("Hold"));

        plan("{\"plan\":\"Sell\",\"targetPrice\":\"30\",\"tags\":[\"core\"]}");
        as(anna, get("/api/v1/audit-log"))
                .andExpect(jsonPath("$[0].action").value("update"))
                .andExpect(jsonPath("$[0].before.plan").value("Hold"))
                .andExpect(jsonPath("$[0].after.plan").value("Sell"));
    }

    @Test
    void savingAPlanUnchangedIsntLoggedAgain() throws Exception {
        plan("{\"plan\":\"Hold\",\"targetPrice\":\"30.50\"}");
        plan("{\"plan\":\"Hold\",\"targetPrice\":\"30.5\"}");

        String body = as(anna, get("/api/v1/audit-log")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$[?(@.entityType == 'plan')].action")).containsExactly("create");
    }

    @Test
    void theLogKeepsMoneyExact() throws Exception {
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountId":"%s","type":"split","date":"2026-02-02","ticker":"PAS","splitFrom":"1234567890.12345678","splitTo":"1","affectsBalance":false}"""
                .formatted(account))).andExpect(status().isCreated());

        assertThat(as(anna, get("/api/v1/audit-log")).andReturn().getResponse().getContentAsString()).contains("1234567890.12345678");
    }
}
