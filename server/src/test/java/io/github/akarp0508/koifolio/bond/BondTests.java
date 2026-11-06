package io.github.akarp0508.koifolio.bond;

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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
class BondTests {

    private static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("Europe/Warsaw"));
    /** A year ago exactly, so each bond is worth a known 105.40 today. */
    private static final LocalDate BOUGHT = TODAY.minusYears(1);

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
        jdbc.sql("DELETE FROM quotes").update();
        jdbc.sql("DELETE FROM fx_rates").update();
        jdbc.sql("DELETE FROM fx_history").update();
        jdbc.sql("DELETE FROM daily_closes").update();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
        account = account("Bonds", "PLN", "40000", ",\"taxRate\":\"19\"");
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

    private String account(String name, String currency, String balance, String extra) throws Exception {
        String body = as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"currency\":\"%s\",\"balance\":\"%s\"%s}".formatted(name, currency, balance, extra)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String series(String kind, LocalDate bought, int term) {
        return kind + YearMonth.from(bought.plusYears(term)).format(DateTimeFormatter.ofPattern("MMyy"));
    }

    private static String tosJson(String accountId, boolean affects) {
        return """
                {"series":"%s","accountId":"%s","quantity":400,"purchaseDate":"%s","rates":["5.40"],"affectsBalance":%s}"""
                .formatted(series("TOS", BOUGHT, 3), accountId, BOUGHT, affects);
    }

    private ResultActions add(String json) throws Exception {
        return as(anna, post("/api/v1/bonds").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String balance() throws Exception {
        return JsonPath.read(as(anna, get("/api/v1/accounts")).andReturn().getResponse().getContentAsString(), "$[0].balance");
    }

    @Test
    void valuesABondFromItsTerms() throws Exception {
        add(tosJson(account, false)).andExpect(status().isCreated());

        as(anna, get("/api/v1/bonds"))
                .andExpect(jsonPath("$[0].kind").value("TOS"))
                .andExpect(jsonPath("$[0].maturityDate").value(BOUGHT.plusYears(3).toString()))
                .andExpect(jsonPath("$[0].cost").value("40000.00"))
                .andExpect(jsonPath("$[0].value").value("42160.00"))
                .andExpect(jsonPath("$[0].interest").value("2160.00"))
                .andExpect(jsonPath("$[0].ifRedeemedToday").value("41760.00"))
                .andExpect(jsonPath("$[0].earlyFee").value("1.00"));
    }

    @Test
    void aTosBoughtBeforeSeptember2024HasTheOldFee() throws Exception {
        add("""
                {"series":"TOS0327","accountId":"%s","quantity":1,"purchaseDate":"2024-03-10","rates":["6"],"affectsBalance":false}"""
                .formatted(account)).andExpect(status().isCreated());

        as(anna, get("/api/v1/bonds")).andExpect(jsonPath("$[0].earlyFee").value("0.70"));
    }

    @Test
    void buyingMovesCashIntoBondsSoTheTotalOnlyGrowsByInterest() throws Exception {
        add(tosJson(account, true)).andExpect(status().isCreated());

        assertThat(balance()).isEqualTo("0.00");
        as(anna, get("/api/v1/dashboard"))
                .andExpect(jsonPath("$.value").value("42160.00"))
                .andExpect(jsonPath("$.bondsValue").value("42160.00"))
                .andExpect(jsonPath("$.bondsGain").value("2160.00"))
                .andExpect(jsonPath("$.cash").value("0.00"));
    }

    @Test
    void backfilledBondsLeaveCashAlone() throws Exception {
        add(tosJson(account, false)).andExpect(status().isCreated());

        assertThat(balance()).isEqualTo("40000.00");
        as(anna, get("/api/v1/dashboard")).andExpect(jsonPath("$.value").value("82160.00"));
    }

    @Test
    void tooLittleCashIsRefused() throws Exception {
        String small = account("Small", "PLN", "10", "");

        add(tosJson(small, true)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("negative_balance"));

        as(anna, get("/api/v1/bonds")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removingABondPutsTheCashBack() throws Exception {
        String id = JsonPath.read(add(tosJson(account, true)).andReturn().getResponse().getContentAsString(), "$.id");

        as(anna, delete("/api/v1/bonds/" + id)).andExpect(status().isNoContent());

        assertThat(balance()).isEqualTo("40000.00");
        as(anna, get("/api/v1/audit-log")).andExpect(jsonPath("$[0].entityType").value("bond"))
                .andExpect(jsonPath("$[0].before.quantity").value(400));
    }

    @Test
    void bondsCountAsLowRiskInBreakdowns() throws Exception {
        add(tosJson(account, true));

        as(anna, get("/api/v1/dashboard"))
                .andExpect(jsonPath("$.bySector[0].label").value("Bonds"))
                .andExpect(jsonPath("$.byRisk[0].label").value("1-2"));
    }

    @Test
    void bondsCountInWeights() throws Exception {
        add(tosJson(account, true));
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountId":"%s","type":"buy","date":"%s","ticker":"PAS","quantity":"10","price":"100","affectsBalance":false}"""
                .formatted(account, TODAY)));
        jdbc.sql("UPDATE instruments SET quote_symbol = 'PAS', quote_currency = 'PLN'").update();
        jdbc.sql("INSERT INTO quotes VALUES ('PAS', 2108, now(), now())").update();

        // 21,080 of stock against 42,160 of bonds
        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].weight").value("33.3"));
    }

    @Test
    void bondsCountTowardsLowRiskRules() throws Exception {
        add(tosJson(account, true));
        as(anna, post("/api/v1/rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"min_low_risk_weight\",\"limitPercent\":\"50\",\"maxRisk\":2}")).andExpect(status().isCreated());

        as(anna, get("/api/v1/rules/check")).andExpect(jsonPath("$[0].passed").value(true))
                .andExpect(jsonPath("$[0].actual").value("100.0"));
    }

    @Test
    void theChartFollowsTheBondDayByDay() throws Exception {
        add(tosJson(account, true));

        String body = as(anna, get("/api/v1/dashboard/history")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.points[0].value")).isEqualTo("40000.00");
        assertThat(JsonPath.<Integer>read(body, "$.points.length()")).isGreaterThan(364);
        assertThat(JsonPath.<String>read(body, "$.points[-1].value")).isEqualTo("42160.00");
        assertThat(JsonPath.<String>read(body, "$.points[-1].gain")).isEqualTo("2160.00");
    }

    @Test
    void theTaxPageShowsWhatRedemptionWouldOwe() throws Exception {
        add(tosJson(account, true));

        as(anna, get("/api/v1/tax"))
                .andExpect(jsonPath("$.totalIfSoldToday").value("410.40"))
                .andExpect(jsonPath("$.openPositions[0].ticker").value(series("TOS", BOUGHT, 3)))
                .andExpect(jsonPath("$.openPositions[0].instrumentId").doesNotExist());
    }

    @Test
    void aRateGapIsFlagged() throws Exception {
        LocalDate bought = TODAY.minusYears(2);
        add("""
                {"series":"%s","accountId":"%s","quantity":1,"purchaseDate":"%s","rates":["6","5"],"earlyFee":"2","affectsBalance":false}"""
                .formatted(series("EDO", bought, 10), account, bought)).andExpect(status().isCreated());

        String id = JsonPath.read(as(anna, get("/api/v1/bonds")).andReturn().getResponse().getContentAsString(), "$[0].id");
        as(anna, get("/api/v1/bonds")).andExpect(jsonPath("$[0].rateAssumed").value(true));

        as(anna, put("/api/v1/bonds/" + id + "/rates").contentType(MediaType.APPLICATION_JSON).content("{\"rates\":[\"6\",\"5\",\"4\"]}"))
                .andExpect(status().isNoContent());

        as(anna, get("/api/v1/bonds")).andExpect(jsonPath("$[0].rateAssumed").value(false));
    }

    @Test
    void badPurchasesAreRefused() throws Exception {
        for (String json : List.of(
                tosJson(account, false).replace(series("TOS", BOUGHT, 3), "TOS0101"),
                tosJson(account, false).replace("TOS", "COI"),
                tosJson(account, false).replace("\"quantity\":400", "\"quantity\":0"),
                tosJson(account, false).replace("\"5.40\"", "\"0\""))) {
            add(json).andExpect(status().isBadRequest());
        }
        add(tosJson(account("Dollars", "USD", "0", ""), false)).andExpect(status().isBadRequest());
        add(tosJson(account, false).replace("TOS", "EDO")).andExpect(status().isBadRequest());
    }

    @Test
    void bondsSurviveExportAndRestore() throws Exception {
        add(tosJson(account, true));
        byte[] zip = as(anna, get("/api/v1/export")).andReturn().getResponse().getContentAsByteArray();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/import")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "export.zip", "application/zip", zip))
                        .param("mode", "replace").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk());

        as(bob, get("/api/v1/bonds"))
                .andExpect(jsonPath("$[0].value").value("42160.00"))
                .andExpect(jsonPath("$[0].accountName").value("Bonds"));
        as(bob, get("/api/v1/dashboard")).andExpect(jsonPath("$.value").value("42160.00"));
    }

    @Test
    void bondsArePrivate() throws Exception {
        String id = JsonPath.read(add(tosJson(account, false)).andReturn().getResponse().getContentAsString(), "$.id");

        as(bob, get("/api/v1/bonds")).andExpect(jsonPath("$.length()").value(0));
        as(bob, delete("/api/v1/bonds/" + id)).andExpect(status().isNotFound());
        as(bob, post("/api/v1/bonds").contentType(MediaType.APPLICATION_JSON).content(tosJson(account, false)))
                .andExpect(status().isNotFound());
    }

    private String agent() throws Exception {
        String body = as(anna, post("/api/v1/agent-tokens").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Claude\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.secret");
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

    private String proposal() {
        return """
                {"series":"%s","account":"%s","quantity":400,"purchaseDate":"%s","rates":["5.40"],"affectsBalance":true}"""
                .formatted(series("TOS", BOUGHT, 3), account, BOUGHT);
    }

    @Test
    void anAgentsBondWaitsUntilApproved() throws Exception {
        String token = agent();

        assertThat(JsonPath.<String>read(call(token, "add_bond", proposal()), "$.summary")).isEqualTo("Add 400 %s bonds".formatted(series("TOS", BOUGHT, 3)));
        as(anna, get("/api/v1/bonds")).andExpect(jsonPath("$.length()").value(0));
        assertThat(balance()).isEqualTo("40000.00");

        as(anna, post("/api/v1/review/approve-all")).andExpect(jsonPath("$.changes").value(1));

        assertThat(balance()).isEqualTo("0.00");
        assertThat(JsonPath.<String>read(call(token, "list_bonds", "{}"), "$[0].value")).isEqualTo("42160.00");
    }

    @Test
    void anApprovedBondCanBeReverted() throws Exception {
        call(agent(), "add_bond", proposal());
        as(anna, post("/api/v1/review/approve-all"));
        String body = as(anna, get("/api/v1/audit-log")).andReturn().getResponse().getContentAsString();
        String id = JsonPath.<List<String>>read(body, "$[?(@.revertible == true)].id").getFirst();

        as(anna, post("/api/v1/review/log/" + id + "/revert")).andExpect(status().isNoContent());

        as(anna, get("/api/v1/bonds")).andExpect(jsonPath("$.length()").value(0));
        assertThat(balance()).isEqualTo("40000.00");
    }
}
