package io.github.akarp0508.koifolio.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import java.time.LocalDate;
import java.util.List;
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
            "get_plan", "get_company", "list_transactions", "check_rules", "get_tax");

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
    void listsOnlyReadToolsAndMarksThem() throws Exception {
        String body = mcp(createToken(anna, "Claude"), "tools/list", "{}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.result.tools[*].name")).containsExactlyInAnyOrderElementsOf(READ_TOOLS);
        assertThat(JsonPath.<List<Boolean>>read(body, "$.result.tools[*].annotations.readOnlyHint")).containsOnly(true);
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
}
