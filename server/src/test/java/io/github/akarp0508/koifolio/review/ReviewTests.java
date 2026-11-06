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
        claude = agent("Claude", "[\"analysis:write\",\"transactions:write\",\"rules:write\"]");
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
}
