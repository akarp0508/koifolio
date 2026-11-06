package io.github.akarp0508.koifolio.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabase.class)
class ReviewTests {

    private static final String MAX_20 = "{\"type\":\"max_position_weight\",\"limitPercent\":\"20\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    private String anna;
    private String bob;
    private String claude;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
        claude = agent("Claude", "[\"analysis:write\",\"transactions:write\",\"accounts:write\",\"rules:write\"]");
    }

    private String tokenFor(String username) throws Exception {
        String credentials = "{\"username\":\"%s\",\"password\":\"correct horse\"}".formatted(username);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String agent(String name, String scopes) throws Exception {
        String body = as(anna, post("/api/v1/agent-tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"scopes\":%s}".formatted(name, scopes)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.secret");
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return as(anna, request).andReturn().getResponse().getContentAsString();
    }

    private String call(String token, String tool, String arguments) throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}"
                .formatted(tool, arguments);
        String body = as(token, post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(body, "$.result.isError")).as(body).isFalse();
        return JsonPath.read(body, "$.result.content[0].text");
    }

    private String instrumentId(String ticker) throws Exception {
        return JsonPath.<List<String>>read(body(get("/api/v1/instruments")), "$[?(@.ticker == '%s')].id".formatted(ticker)).getFirst();
    }

    private void company(String ticker, String json) throws Exception {
        as(anna, post("/api/v1/instruments").contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("{", "{\"ticker\":\"%s\",".formatted(ticker)))).andExpect(status().isCreated());
    }

    private String ruleId() throws Exception {
        return JsonPath.read(body(get("/api/v1/rules")), "$[0].id");
    }

    private String pendingId() throws Exception {
        return JsonPath.read(body(get("/api/v1/review/changes")), "$[0].id");
    }

    private void approveNext() throws Exception {
        as(anna, post("/api/v1/review/changes/" + pendingId() + "/approve")).andExpect(status().isNoContent());
    }

    private String lastAgentChange() throws Exception {
        return JsonPath.<List<String>>read(body(get("/api/v1/audit-log")), "$[?(@.revertible == true)].id").getFirst();
    }

    private ResultActions revert(String id) throws Exception {
        return as(anna, post("/api/v1/review/log/" + id + "/revert"));
    }

    @Test
    void aCompanyEditWaitsAndChangesNothing() throws Exception {
        company("PAS", "{\"sector\":\"Tech\",\"risk\":3}");

        String proposed = call(claude, "update_company", "{\"ticker\":\"pas\",\"risk\":2}");

        assertThat(JsonPath.<String>read(proposed, "$.status")).isEqualTo("waiting for review");
        assertThat(JsonPath.<Integer>read(call(claude, "get_company", "{\"ticker\":\"PAS\"}"), "$.risk")).isEqualTo(3);
        as(anna, get("/api/v1/review/changes"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].agentName").value("Claude"))
                .andExpect(jsonPath("$[0].summary").value("Change PAS: risk"))
                .andExpect(jsonPath("$[0].current.risk").value(3))
                .andExpect(jsonPath("$[0].current.sector").doesNotExist())
                .andExpect(jsonPath("$[0].proposed.risk").value(2));
        as(anna, get("/api/v1/dashboard")).andExpect(jsonPath("$.pendingChanges").value(1));
        assertThat(JsonPath.<String>read(call(claude, "list_pending_changes", "{}"), "$[0].summary")).isEqualTo("Change PAS: risk");
    }

    @Test
    void approvingAppliesOnlyWhatWasProposed() throws Exception {
        company("PAS", "{\"sector\":\"Tech\",\"thesis\":\"Cheap\",\"tags\":[\"core\"]}");
        call(claude, "update_company", "{\"ticker\":\"PAS\",\"risk\":2}");

        approveNext();

        String company = call(claude, "get_company", "{\"ticker\":\"PAS\"}");
        assertThat(JsonPath.<Integer>read(company, "$.risk")).isEqualTo(2);
        assertThat(JsonPath.<String>read(company, "$.sector")).isEqualTo("Tech");
        assertThat(JsonPath.<String>read(company, "$.thesis")).isEqualTo("Cheap");
        assertThat(JsonPath.<List<String>>read(company, "$.tags")).containsExactly("core");
        as(anna, get("/api/v1/review/changes")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void yourEditsInTheMeantimeSurviveApproval() throws Exception {
        company("PAS", "{\"sector\":\"Tech\"}");
        call(claude, "update_company", "{\"ticker\":\"PAS\",\"risk\":2}");
        as(anna, put("/api/v1/instruments/" + instrumentId("PAS")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sector\":\"Energy\"}")).andExpect(status().isOk());

        approveNext();

        String company = call(claude, "get_company", "{\"ticker\":\"PAS\"}");
        assertThat(JsonPath.<String>read(company, "$.sector")).isEqualTo("Energy");
        assertThat(JsonPath.<Integer>read(company, "$.risk")).isEqualTo(2);
    }

    @Test
    void aNewCompanyAppearsOnlyOnceApproved() throws Exception {
        call(claude, "update_company", "{\"ticker\":\"new\",\"name\":\"New Co\"}");
        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$.length()").value(0));

        approveNext();

        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$[0].ticker").value("NEW"))
                .andExpect(jsonPath("$[0].name").value("New Co"));
    }

    @Test
    void rejectingDropsTheChange() throws Exception {
        company("PAS", "{\"risk\":3}");
        call(claude, "update_company", "{\"ticker\":\"PAS\",\"risk\":2}");

        as(anna, post("/api/v1/review/changes/" + pendingId() + "/reject")).andExpect(status().isNoContent());

        assertThat(JsonPath.<Integer>read(call(claude, "get_company", "{\"ticker\":\"PAS\"}"), "$.risk")).isEqualTo(3);
        as(anna, get("/api/v1/review/changes")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aProposalWithNothingInItIsRefused() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"update_company\",\"arguments\":{\"ticker\":\"PAS\"}}}";
        as(claude, post("/mcp").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(json))
                .andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    void ruleChangesWaitToo() throws Exception {
        call(claude, "save_rule", MAX_20);
        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$.length()").value(0));
        approveNext();
        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$[0].limitPercent").value("20"));

        String id = ruleId();
        call(claude, "save_rule", "{\"id\":\"%s\",\"type\":\"max_position_weight\",\"limitPercent\":\"25\"}".formatted(id));
        as(anna, get("/api/v1/review/changes")).andExpect(jsonPath("$[0].current.limitPercent").value(20));
        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$[0].limitPercent").value("20"));
        approveNext();
        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$[0].limitPercent").value("25"));

        call(claude, "delete_rule", "{\"id\":\"%s\"}".formatted(id));
        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$.length()").value(1));
        approveNext();
        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theLogShowsTheProposalTheChangeAndTheApproval() throws Exception {
        company("PAS", "{\"risk\":3}");
        call(claude, "update_company", "{\"ticker\":\"PAS\",\"risk\":2}");
        approveNext();

        as(anna, get("/api/v1/audit-log"))
                .andExpect(jsonPath("$[0].summary").value("Approved: Change PAS: risk"))
                .andExpect(jsonPath("$[0].actorType").value("USER"))
                .andExpect(jsonPath("$[1].summary").value("Changed PAS"))
                .andExpect(jsonPath("$[1].actorName").value("Claude"))
                .andExpect(jsonPath("$[1].before.risk").value(3))
                .andExpect(jsonPath("$[1].after.risk").value(2))
                .andExpect(jsonPath("$[2].summary").value("Proposed: Change PAS: risk"))
                .andExpect(jsonPath("$[2].actorName").value("Claude"));
    }

    @Test
    void revertRestoresACompanyInFull() throws Exception {
        String analysis = "Long analysis ".repeat(50).strip();
        company("PAS", "{\"analysis\":\"%s\",\"risk\":3}".formatted(analysis));
        call(claude, "update_company", "{\"ticker\":\"PAS\",\"analysis\":\"Overwritten\",\"risk\":1}");
        approveNext();

        revert(lastAgentChange()).andExpect(status().isNoContent());

        String company = call(claude, "get_company", "{\"ticker\":\"PAS\"}");
        assertThat(JsonPath.<String>read(company, "$.analysis")).isEqualTo(analysis);
        assertThat(JsonPath.<Integer>read(company, "$.risk")).isEqualTo(3);
    }

    @Test
    void aChangeCanOnlyBeRevertedOnce() throws Exception {
        company("PAS", "{\"risk\":3}");
        call(claude, "update_company", "{\"ticker\":\"PAS\",\"risk\":2}");
        approveNext();
        String id = lastAgentChange();

        revert(id).andExpect(status().isNoContent());

        revert(id).andExpect(status().isConflict());
        assertThat(JsonPath.<List<String>>read(body(get("/api/v1/audit-log")), "$[?(@.revertible == true)].id")).isEmpty();
    }

    @Test
    void revertRemovesACompanyTheAgentAdded() throws Exception {
        call(claude, "update_company", "{\"ticker\":\"NEW\",\"name\":\"New Co\"}");
        approveNext();

        revert(lastAgentChange()).andExpect(status().isNoContent());

        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void revertUndoesRuleChanges() throws Exception {
        call(claude, "save_rule", MAX_20);
        approveNext();
        String id = ruleId();

        call(claude, "save_rule", "{\"id\":\"%s\",\"type\":\"max_position_weight\",\"limitPercent\":\"25\"}".formatted(id));
        approveNext();
        revert(lastAgentChange()).andExpect(status().isNoContent());
        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$[0].limitPercent").value("20"));

        call(claude, "delete_rule", "{\"id\":\"%s\"}".formatted(id));
        approveNext();
        revert(lastAgentChange()).andExpect(status().isNoContent());
        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$[0].limitPercent").value("20"));
    }

    @Test
    void revertRemovesARuleTheAgentAdded() throws Exception {
        call(claude, "save_rule", MAX_20);
        approveNext();

        revert(lastAgentChange()).andExpect(status().isNoContent());

        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void revertWarnsIfItChangedSince() throws Exception {
        company("PAS", "{\"risk\":3}");
        call(claude, "update_company", "{\"ticker\":\"PAS\",\"risk\":2}");
        approveNext();
        String id = lastAgentChange();
        as(anna, put("/api/v1/instruments/" + instrumentId("PAS")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"risk\":5}")).andExpect(status().isOk());

        as(anna, get("/api/v1/audit-log")).andExpect(jsonPath("$[?(@.revertible == true)].changedSince").value(true));
        revert(id).andExpect(status().isConflict());
        assertThat(JsonPath.<Integer>read(call(claude, "get_company", "{\"ticker\":\"PAS\"}"), "$.risk")).isEqualTo(5);

        as(anna, post("/api/v1/review/log/" + id + "/revert").param("force", "true")).andExpect(status().isNoContent());
        assertThat(JsonPath.<Integer>read(call(claude, "get_company", "{\"ticker\":\"PAS\"}"), "$.risk")).isEqualTo(3);
    }

    @Test
    void yourOwnChangesAndProposalsCantBeReverted() throws Exception {
        company("PAS", "{\"risk\":3}");
        String mine = JsonPath.read(body(get("/api/v1/audit-log")), "$[0].id");
        revert(mine).andExpect(status().isConflict());

        call(claude, "update_company", "{\"ticker\":\"PAS\",\"risk\":2}");
        String proposal = JsonPath.read(body(get("/api/v1/audit-log")), "$[0].id");
        revert(proposal).andExpect(status().isConflict());
    }

    private void deposit(String token, String account, String amount) throws Exception {
        call(token, "add_transaction", """
                {"account":"%s","type":"deposit","date":"2026-02-02","affectsBalance":true,"amount":"%s"}""".formatted(account, amount));
    }

    private String account() throws Exception {
        String body = as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Main\",\"currency\":\"PLN\",\"balance\":\"100\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void rejectAllUndoesEverythingWaiting() throws Exception {
        String account = account();
        deposit(claude, account, "10");
        deposit(claude, account, "20");
        call(claude, "save_rule", MAX_20);

        as(anna, post("/api/v1/review/reject-all"))
                .andExpect(jsonPath("$.transactions").value(2))
                .andExpect(jsonPath("$.changes").value(1));

        assertThat(JsonPath.<String>read(body(get("/api/v1/accounts")), "$[0].balance")).isEqualTo("100.00");
        as(anna, get("/api/v1/dashboard"))
                .andExpect(jsonPath("$.unvalidated").value(0))
                .andExpect(jsonPath("$.pendingChanges").value(0));
        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void decidingAllCanBeLimitedToOneAgent() throws Exception {
        String account = account();
        String other = agent("Other", "[\"transactions:write\",\"rules:write\"]");
        deposit(claude, account, "10");
        deposit(other, account, "20");
        call(other, "save_rule", MAX_20);

        as(anna, post("/api/v1/review/reject-all").param("agent", "Other"))
                .andExpect(jsonPath("$.transactions").value(1))
                .andExpect(jsonPath("$.changes").value(1));

        as(anna, get("/api/v1/transactions").param("unvalidated", "true"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].createdBy").value("Claude"));
        as(anna, post("/api/v1/review/approve-all").param("agent", "Claude")).andExpect(jsonPath("$.transactions").value(1));
    }

    @Test
    void approveAllAppliesTransactionsAndChanges() throws Exception {
        deposit(claude, account(), "10");
        call(claude, "save_rule", MAX_20);

        as(anna, post("/api/v1/review/approve-all"))
                .andExpect(jsonPath("$.transactions").value(1))
                .andExpect(jsonPath("$.changes").value(1));

        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aChangeToARuleThatsGoneStaysVisibleAndCanBeRejected() throws Exception {
        call(claude, "save_rule", MAX_20);
        approveNext();
        String id = ruleId();
        call(claude, "delete_rule", "{\"id\":\"%s\"}".formatted(id));
        as(anna, delete("/api/v1/rules/" + id)).andExpect(status().isNoContent());

        as(anna, get("/api/v1/review/changes")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        as(anna, post("/api/v1/review/changes/" + pendingId() + "/approve")).andExpect(status().isNotFound());
        as(anna, post("/api/v1/review/changes/" + pendingId() + "/reject")).andExpect(status().isNoContent());
    }

    @Test
    void theCountCoversTransactionsAndChanges() throws Exception {
        as(anna, get("/api/v1/review/count")).andExpect(jsonPath("$.waiting").value(0));

        deposit(claude, account(), "10");
        call(claude, "save_rule", MAX_20);

        as(anna, get("/api/v1/review/count")).andExpect(jsonPath("$.waiting").value(2));
        as(bob, get("/api/v1/review/count")).andExpect(jsonPath("$.waiting").value(0));

        as(anna, post("/api/v1/review/reject-all"));
        as(anna, get("/api/v1/review/count")).andExpect(jsonPath("$.waiting").value(0));
    }

    private static final String IKE = """
            {"name":"IKE","currency":"pln","balance":"2301.96","taxRate":"0","costMethod":"fifo","description":"At XTB"}""";

    private String accountsBody() throws Exception {
        return body(get("/api/v1/accounts"));
    }

    @Test
    void anAccountWaitsUntilApproved() throws Exception {
        String proposed = call(claude, "create_account", IKE);

        assertThat(JsonPath.<String>read(proposed, "$.status")).isEqualTo("waiting for review");
        as(anna, get("/api/v1/accounts")).andExpect(jsonPath("$.length()").value(0));
        as(anna, get("/api/v1/review/changes"))
                .andExpect(jsonPath("$[0].summary").value("Create account IKE (PLN, balance 2301.96)"))
                .andExpect(jsonPath("$[0].current").doesNotExist())
                .andExpect(jsonPath("$[0].proposed.currency").value("PLN"));

        approveNext();

        as(anna, get("/api/v1/accounts"))
                .andExpect(jsonPath("$[0].name").value("IKE"))
                .andExpect(jsonPath("$[0].balance").value("2301.96"))
                .andExpect(jsonPath("$[0].taxRate").value("0"))
                .andExpect(jsonPath("$[0].costMethod").value("fifo"))
                .andExpect(jsonPath("$[0].description").value("At XTB"));
        as(anna, get("/api/v1/audit-log"))
                .andExpect(jsonPath("$[1].summary").value("Added account IKE"))
                .andExpect(jsonPath("$[1].actorName").value("Claude"));
    }

    @Test
    void anApprovedAccountTakesTransactions() throws Exception {
        call(claude, "create_account", IKE);
        approveNext();
        String id = JsonPath.read(accountsBody(), "$[0].id");

        deposit(claude, id, "10");

        assertThat(JsonPath.<String>read(accountsBody(), "$[0].balance")).isEqualTo("2311.96");
    }

    @Test
    void rejectingAnAccountCreatesNothing() throws Exception {
        call(claude, "create_account", IKE);

        as(anna, post("/api/v1/review/reject-all")).andExpect(jsonPath("$.changes").value(1));

        as(anna, get("/api/v1/accounts")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aBadAccountIsRefusedWhenProposed() throws Exception {
        for (String arguments : List.of("{\"name\":\"X\",\"currency\":\"ZZZ\"}", "{\"name\":\" \",\"currency\":\"PLN\"}",
                "{\"name\":\"X\",\"currency\":\"PLN\",\"balance\":\"1.234\"}",
                "{\"name\":\"X\",\"currency\":\"PLN\",\"taxRate\":\"140\"}",
                "{\"name\":\"X\",\"currency\":\"PLN\",\"costMethod\":\"lifo\"}")) {
            String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"create_account\",\"arguments\":%s}}"
                    .formatted(arguments);
            as(claude, post("/mcp").contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(json))
                    .andExpect(jsonPath("$.result.isError").value(true));
        }
        as(anna, get("/api/v1/review/changes")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void revertRemovesAnEmptyAccount() throws Exception {
        call(claude, "create_account", IKE);
        approveNext();

        revert(lastAgentChange()).andExpect(status().isNoContent());

        as(anna, get("/api/v1/accounts")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void revertKeepsAnAccountWithTransactions() throws Exception {
        call(claude, "create_account", IKE);
        approveNext();
        String change = lastAgentChange();
        deposit(claude, JsonPath.read(accountsBody(), "$[0].id"), "10");

        revert(change).andExpect(status().isConflict());

        as(anna, get("/api/v1/accounts")).andExpect(jsonPath("$.length()").value(1));
        as(anna, get("/api/v1/transactions")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void existingTokensDontGainThePermission() throws Exception {
        String old = agent("Old", "[\"transactions:write\",\"analysis:write\"]");
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"create_account\",\"arguments\":%s}}"
                .formatted(IKE);

        as(old, post("/mcp").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(json)).andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    void reviewIsPrivate() throws Exception {
        call(claude, "save_rule", MAX_20);
        String id = pendingId();

        as(bob, get("/api/v1/review/changes")).andExpect(jsonPath("$.length()").value(0));
        as(bob, post("/api/v1/review/changes/" + id + "/approve")).andExpect(status().isNotFound());
        as(bob, post("/api/v1/review/changes/" + id + "/reject")).andExpect(status().isNotFound());
        as(bob, post("/api/v1/review/reject-all")).andExpect(jsonPath("$.changes").value(0));
        as(anna, get("/api/v1/review/changes")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anotherUserCantRevertYourChanges() throws Exception {
        call(claude, "save_rule", MAX_20);
        approveNext();

        as(bob, post("/api/v1/review/log/" + lastAgentChange() + "/revert")).andExpect(status().isNotFound());
    }

    private String agentDeposit(String account, String amount) throws Exception {
        String added = call(claude, "add_transaction",
                "{\"account\":\"%s\",\"type\":\"deposit\",\"date\":\"2026-03-12\",\"affectsBalance\":true,\"amount\":\"%s\"}"
                        .formatted(account, amount));
        return JsonPath.read(added, "$.id");
    }

    private String balanceOf(String account) throws Exception {
        return JsonPath.read(body(get("/api/v1/accounts/" + account)), "$.balance");
    }

    @Test
    void aTransactionEditWaitsThenAppliesOverTheCurrentValues() throws Exception {
        String account = account();
        String id = agentDeposit(account, "100");
        as(anna, post("/api/v1/transactions/" + id + "/approve")).andExpect(status().isOk());

        call(claude, "update_transaction", "{\"id\":\"%s\",\"amount\":\"150\"}".formatted(id));

        assertThat(balanceOf(account)).isEqualTo("200.00");
        as(anna, get("/api/v1/review/changes"))
                .andExpect(jsonPath("$[0].summary").value("Change deposit on 2026-03-12: amount"))
                .andExpect(jsonPath("$[0].current.amount").value(100.0))
                .andExpect(jsonPath("$[0].proposed.amount").value(150));

        approveNext();

        assertThat(balanceOf(account)).isEqualTo("250.00");
        as(anna, get("/api/v1/transactions/" + id))
                .andExpect(jsonPath("$.amount").value("150.00"))
                .andExpect(jsonPath("$.date").value("2026-03-12"))
                .andExpect(jsonPath("$.unvalidated").value(false));
        assertThat(lastAgentChange()).isNotNull();
    }

    @Test
    void aBadTransactionEditIsRefusedWhenProposed() throws Exception {
        String id = agentDeposit(account(), "100");

        assertThat(toolError("update_transaction", "{\"id\":\"%s\",\"amount\":\"-5\"}".formatted(id))).contains("amount");
        assertThat(toolError("update_transaction", "{\"id\":\"%s\"}".formatted(id))).contains("at least one field");
        assertThat(toolError("update_transaction", "{\"id\":\"%s\",\"type\":\"buy\"}".formatted(id))).contains("ticker");
        as(anna, get("/api/v1/review/count")).andExpect(jsonPath("$.waiting").value(1));
    }

    @Test
    void revertPutsATransactionBackWithItsBalance() throws Exception {
        String account = account();
        String id = agentDeposit(account, "100");
        call(claude, "update_transaction", "{\"id\":\"%s\",\"amount\":\"150\"}".formatted(id));
        approveNext();

        as(anna, post("/api/v1/review/log/" + lastAgentChange() + "/revert")).andExpect(status().isNoContent());

        assertThat(balanceOf(account)).isEqualTo("200.00");
        as(anna, get("/api/v1/transactions/" + id)).andExpect(jsonPath("$.amount").value("100.00"));
    }

    @Test
    void aTransactionDeleteWaitsAndCanBeReverted() throws Exception {
        String account = account();
        String id = agentDeposit(account, "100");
        as(anna, post("/api/v1/transactions/" + id + "/approve")).andExpect(status().isOk());

        call(claude, "delete_transaction", "{\"id\":\"%s\"}".formatted(id));

        assertThat(balanceOf(account)).isEqualTo("200.00");
        as(anna, get("/api/v1/review/changes")).andExpect(jsonPath("$[0].current.amount").value(100.0));

        approveNext();

        assertThat(balanceOf(account)).isEqualTo("100.00");
        as(anna, get("/api/v1/transactions/" + id)).andExpect(status().isNotFound());

        as(anna, post("/api/v1/review/log/" + lastAgentChange() + "/revert")).andExpect(status().isNoContent());

        assertThat(balanceOf(account)).isEqualTo("200.00");
        as(anna, get("/api/v1/transactions/" + id)).andExpect(jsonPath("$.amount").value("100.00"));
    }

    @Test
    void approveAllHandlesADeleteOfAWaitingTransaction() throws Exception {
        String account = account();
        String id = agentDeposit(account, "100");
        call(claude, "delete_transaction", "{\"id\":\"%s\"}".formatted(id));

        as(anna, post("/api/v1/review/approve-all"))
                .andExpect(jsonPath("$.changes").value(1))
                .andExpect(jsonPath("$.transactions").value(0));

        assertThat(balanceOf(account)).isEqualTo("100.00");
    }

    @Test
    void anAgentCantTouchAnotherUsersTransaction() throws Exception {
        String account = account();
        String id = JsonPath.read(as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(
                "{\"accountId\":\"%s\",\"type\":\"deposit\",\"date\":\"2026-03-12\",\"amount\":\"10\",\"affectsBalance\":true}".formatted(account)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String other = bobsAgent();

        assertThat(toolError(other, "delete_transaction", "{\"id\":\"%s\"}".formatted(id))).contains("not found");
    }

    @Test
    void anAccountEditWaitsAndAppliesOnlyWhatWasProposed() throws Exception {
        String account = account();

        call(claude, "update_account", "{\"id\":\"%s\",\"description\":\"Long term\",\"taxRate\":\"19\"}".formatted(account));

        as(anna, get("/api/v1/accounts/" + account)).andExpect(jsonPath("$.taxRate").doesNotExist());
        approveNext();

        as(anna, get("/api/v1/accounts/" + account))
                .andExpect(jsonPath("$.description").value("Long term"))
                .andExpect(jsonPath("$.taxRate").value("19"));
    }

    @Test
    void revertingABalanceEditKeepsTransactionsMadeSince() throws Exception {
        String account = account();
        call(claude, "update_account", "{\"id\":\"%s\",\"balance\":\"500\"}".formatted(account));
        approveNext();
        String edit = lastAgentChange();
        agentDeposit(account, "100");

        as(anna, post("/api/v1/review/log/" + edit + "/revert")).andExpect(status().isNoContent());

        assertThat(balanceOf(account)).isEqualTo("200.00");
    }

    @Test
    void aBadAccountEditIsRefusedWhenProposed() throws Exception {
        String account = account();

        assertThat(toolError("update_account", "{\"id\":\"%s\",\"balance\":\"1.234\"}".formatted(account))).contains("decimal places");
        assertThat(toolError("update_account", "{\"id\":\"%s\"}".formatted(account))).contains("at least one field");
    }

    private String bobsAgent() throws Exception {
        String body = as(bob, post("/api/v1/agent-tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Other\",\"scopes\":[\"transactions:write\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.secret");
    }

    private String toolError(String tool, String arguments) throws Exception {
        return toolError(claude, tool, arguments);
    }

    private String toolError(String token, String tool, String arguments) throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}"
                .formatted(tool, arguments);
        String body = as(token, post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(body, "$.result.isError")).as(body).isTrue();
        return JsonPath.read(body, "$.result.content[0].text");
    }

    private static String depositRow(String account, String amount) {
        return "{\"account\":\"%s\",\"type\":\"deposit\",\"date\":\"2026-03-12\",\"affectsBalance\":true,\"amount\":\"%s\"}"
                .formatted(account, amount);
    }

    private String imported(String... rows) throws Exception {
        return call(claude, "add_transactions", "{\"transactions\":[%s]}".formatted(String.join(",", rows)));
    }

    @Test
    void anImportAddsEveryRowAndGroupsThem() throws Exception {
        String account = account();

        String result = imported(depositRow(account, "10"), depositRow(account, "20"));

        assertThat(JsonPath.<Boolean>read(result, "$.added")).isTrue();
        assertThat(JsonPath.<List<String>>read(result, "$.rows[*].status")).containsExactly("added", "added");
        assertThat(balanceOf(account)).isEqualTo("130.00");
        String importId = JsonPath.read(result, "$.importId");
        as(anna, get("/api/v1/transactions").param("unvalidated", "true"))
                .andExpect(jsonPath("$[0].importId").value(importId))
                .andExpect(jsonPath("$[1].importId").value(importId));
    }

    @Test
    void anImportWithABadRowAddsNothingAndSaysWhich() throws Exception {
        String account = account();

        String result = imported(depositRow(account, "10"), depositRow(account, "-5"),
                "{\"account\":\"%s\",\"type\":\"buy\",\"date\":\"2026-03-12\",\"affectsBalance\":true}".formatted(account));

        assertThat(JsonPath.<Boolean>read(result, "$.added")).isFalse();
        assertThat(JsonPath.<List<String>>read(result, "$.rows[*].status")).containsExactly("valid", "refused", "refused");
        assertThat(JsonPath.<String>read(result, "$.rows[1].error")).contains("amount");
        assertThat(JsonPath.<String>read(result, "$.rows[2].error")).contains("ticker");
        assertThat(balanceOf(account)).isEqualTo("100.00");
        as(anna, get("/api/v1/review/count")).andExpect(jsonPath("$.waiting").value(0));
        assertThat(JsonPath.<List<?>>read(body(get("/api/v1/audit-log")), "$[?(@.entityType == 'transaction')]")).isEmpty();
    }

    @Test
    void aRowCantTakeTheBalanceBelowZeroWithoutSayingSo() throws Exception {
        String account = account();
        String withdrawal = "{\"account\":\"%s\",\"type\":\"withdrawal\",\"date\":\"2026-03-12\",\"affectsBalance\":true,\"amount\":\"80\"%s}";

        String refused = imported(withdrawal.formatted(account, ""), withdrawal.formatted(account, ""));
        assertThat(JsonPath.<List<String>>read(refused, "$.rows[*].status")).containsExactly("valid", "refused");

        String added = imported(withdrawal.formatted(account, ""), withdrawal.formatted(account, ",\"allowNegativeBalance\":true"));
        assertThat(JsonPath.<Boolean>read(added, "$.added")).isTrue();
        assertThat(balanceOf(account)).isEqualTo("-60.00");
    }

    @Test
    void anImportCanBeDecidedOnItsOwn() throws Exception {
        String account = account();
        String first = JsonPath.read(imported(depositRow(account, "10"), depositRow(account, "20")), "$.importId");
        String second = JsonPath.read(imported(depositRow(account, "30")), "$.importId");
        agentDeposit(account, "40");
        call(claude, "update_account", "{\"id\":\"%s\",\"description\":\"Long term\"}".formatted(account));

        as(anna, post("/api/v1/review/approve-all").param("importId", first))
                .andExpect(jsonPath("$.transactions").value(2))
                .andExpect(jsonPath("$.changes").value(0));
        as(anna, post("/api/v1/review/reject-all").param("importId", second))
                .andExpect(jsonPath("$.transactions").value(1))
                .andExpect(jsonPath("$.changes").value(0));

        assertThat(balanceOf(account)).isEqualTo("170.00");
        as(anna, get("/api/v1/review/count")).andExpect(jsonPath("$.waiting").value(2));
    }

    @Test
    void aNoteCanBeProposedAndRevertedToNone() throws Exception {
        String id = agentDeposit(account(), "100");

        call(claude, "update_transaction", "{\"id\":\"%s\",\"note\":\"  Paid in for the Q3 dip  \"}".formatted(id));
        approveNext();
        as(anna, get("/api/v1/transactions/" + id)).andExpect(jsonPath("$.note").value("Paid in for the Q3 dip"));

        as(anna, post("/api/v1/review/log/" + lastAgentChange() + "/revert")).andExpect(status().isNoContent());

        as(anna, get("/api/v1/transactions/" + id)).andExpect(jsonPath("$.note").doesNotExist());
    }

    @Test
    void anEditKeepsANoteItDoesntMention() throws Exception {
        String account = account();
        String added = call(claude, "add_transaction", """
                {"account":"%s","type":"deposit","date":"2026-03-12","affectsBalance":true,"amount":"10","note":"Why"}""".formatted(account));
        String id = JsonPath.read(added, "$.id");

        call(claude, "update_transaction", "{\"id\":\"%s\",\"amount\":\"20\"}".formatted(id));
        approveNext();

        as(anna, get("/api/v1/transactions/" + id)).andExpect(jsonPath("$.note").value("Why"));
    }
}
