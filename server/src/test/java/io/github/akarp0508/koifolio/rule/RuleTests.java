package io.github.akarp0508.koifolio.rule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import java.math.BigDecimal;
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
class RuleTests {

    private static final String MAX_20 = "{\"type\":\"max_position_weight\",\"limitPercent\":\"20\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private String anna;
    private String bob;
    private String ike;
    private String xtb;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        jdbc.sql("DELETE FROM quotes").update();
        jdbc.sql("DELETE FROM fx_rates").update();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
        ike = createAccount("IKE", "PLN", "1000");
        xtb = createAccount("XTB", "USD", "0");
        jdbc.sql("INSERT INTO fx_rates VALUES ('USD', 4, current_date, now())").update();
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

    private ResultActions send(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
        return as(token, request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createAccount(String name, String currency, String balance) throws Exception {
        String body = send(anna, post("/api/v1/accounts"),
                "{\"name\":\"%s\",\"currency\":\"%s\",\"balance\":\"%s\"}".formatted(name, currency, balance))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String rule(String json) throws Exception {
        String body = send(anna, post("/api/v1/rules"), json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void hold(String account, String ticker, String quantity, String price, String currency) throws Exception {
        send(anna, post("/api/v1/transactions"), """
                {"accountId":"%s","type":"buy","date":"2026-01-01","ticker":"%s","quantity":"%s","price":"1","affectsBalance":false}"""
                .formatted(account, ticker, quantity)).andExpect(status().isCreated());
        if (price != null) {
            jdbc.sql("UPDATE instruments SET quote_symbol = ?, quote_currency = ? WHERE ticker = ?")
                    .params(ticker, currency, ticker).update();
            jdbc.sql("""
                    INSERT INTO quotes VALUES (?, ?, now(), now())
                    ON CONFLICT (symbol) DO UPDATE SET price = excluded.price""").params(ticker, new BigDecimal(price)).update();
        }
    }

    private void risk(String account, String ticker, int risk) throws Exception {
        send(anna, put("/api/v1/positions/%s/%s/plan".formatted(account, ticker)), "{\"risk\":%d}".formatted(risk))
                .andExpect(status().isOk());
    }

    @Test
    void createsListsEditsAndDeletes() throws Exception {
        String id = rule(MAX_20);

        as(anna, get("/api/v1/rules")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("max_position_weight"))
                .andExpect(jsonPath("$[0].limitPercent").value("20"))
                .andExpect(jsonPath("$[0].accountId").doesNotExist());

        send(anna, put("/api/v1/rules/" + id), """
                {"type":"min_low_risk_weight","accountId":"%s","limitPercent":"50.5","maxRisk":2}""".formatted(ike))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitPercent").value("50.5"))
                .andExpect(jsonPath("$.accountId").value(ike))
                .andExpect(jsonPath("$.maxRisk").value(2));

        as(anna, delete("/api/v1/rules/" + id)).andExpect(status().isNoContent());
        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aMaximumNamesTheCompanyOverIt() throws Exception {
        rule(MAX_20);
        hold(ike, "PAS", "300", "1", "PLN");

        as(anna, get("/api/v1/rules/check")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passed").value(false))
                .andExpect(jsonPath("$[0].actual").value("23.1"))
                .andExpect(jsonPath("$[0].breaches[0].ticker").value("PAS"))
                .andExpect(jsonPath("$[0].rule.limitPercent").value("20"));
    }

    @Test
    void aCompanyAddsUpAcrossAccountsInTheBaseCurrency() throws Exception {
        rule("{\"type\":\"max_position_weight\",\"limitPercent\":\"50\"}");
        hold(ike, "PAS", "100", "4", "PLN");
        hold(xtb, "PAS", "100", "4", "PLN");

        as(anna, get("/api/v1/rules/check")).andExpect(jsonPath("$[0].actual").value("44.4"));
    }

    @Test
    void anAccountRuleLooksOnlyAtThatAccount() throws Exception {
        rule("{\"type\":\"max_position_weight\",\"limitPercent\":\"60\",\"accountId\":\"%s\"}".formatted(ike));
        hold(ike, "PAS", "1000", "1", "PLN");
        hold(xtb, "ABC", "100000", "1", "USD");

        as(anna, get("/api/v1/rules/check"))
                .andExpect(jsonPath("$[0].passed").value(true))
                .andExpect(jsonPath("$[0].actual").value("50.0"));
    }

    @Test
    void lowRiskCountsPlansAndCash() throws Exception {
        rule("{\"type\":\"min_low_risk_weight\",\"limitPercent\":\"50\",\"maxRisk\":2}");
        hold(ike, "PAS", "1000", "1", "PLN");
        hold(ike, "ABC", "2000", "1", "PLN");
        risk(ike, "PAS", 2);
        risk(ike, "ABC", 5);

        as(anna, get("/api/v1/rules/check"))
                .andExpect(jsonPath("$[0].passed").value(true))
                .andExpect(jsonPath("$[0].actual").value("50.0"));
    }

    @Test
    void unpricedPositionsAreCounted() throws Exception {
        rule(MAX_20);
        hold(ike, "PAS", "1", null, null);

        as(anna, get("/api/v1/rules/check"))
                .andExpect(jsonPath("$[0].passed").value(true))
                .andExpect(jsonPath("$[0].unpriced").value(1));
    }

    @Test
    void aLowRiskRuleNeedsAMaxRiskAndOnlyItMayHaveOne() throws Exception {
        send(anna, post("/api/v1/rules"), "{\"type\":\"min_low_risk_weight\",\"limitPercent\":\"50\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("maxRisk"));
        send(anna, post("/api/v1/rules"), "{\"type\":\"max_position_weight\",\"limitPercent\":\"20\",\"maxRisk\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("maxRisk"));
    }

    @Test
    void limitsStayBetweenZeroAndAHundred() throws Exception {
        for (String limit : new String[] {"0", "-1", "100.01"}) {
            send(anna, post("/api/v1/rules"), "{\"type\":\"max_position_weight\",\"limitPercent\":\"%s\"}".formatted(limit))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rulesArePrivate() throws Exception {
        String id = rule(MAX_20);

        as(bob, get("/api/v1/rules")).andExpect(jsonPath("$.length()").value(0));
        as(bob, get("/api/v1/rules/check")).andExpect(jsonPath("$.length()").value(0));
        send(bob, put("/api/v1/rules/" + id), MAX_20).andExpect(status().isNotFound());
        as(bob, delete("/api/v1/rules/" + id)).andExpect(status().isNotFound());
        send(bob, post("/api/v1/rules"), "{\"type\":\"max_position_weight\",\"limitPercent\":\"20\",\"accountId\":\"%s\"}".formatted(ike))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAnAccountDeletesItsRules() throws Exception {
        rule("{\"type\":\"max_position_weight\",\"limitPercent\":\"20\",\"accountId\":\"%s\"}".formatted(ike));

        as(anna, delete("/api/v1/accounts/" + ike)).andExpect(status().isNoContent());

        as(anna, get("/api/v1/rules")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/v1/rules")).andExpect(status().isUnauthorized());
    }
}
