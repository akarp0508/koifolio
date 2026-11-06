package io.github.akarp0508.koifolio.position;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
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
class PositionPlanTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private String anna;
    private String bob;
    private String ike;
    private String brokerage;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        jdbc.sql("DELETE FROM quotes").update();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
        ike = createAccount("IKE");
        brokerage = createAccount("Brokerage");
        trade(ike, "buy", "PAS", "10");
        trade(brokerage, "buy", "PAS", "5");
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

    private String createAccount(String name) throws Exception {
        String body = as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"currency\":\"PLN\",\"balance\":\"100000\"}".formatted(name)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void trade(String account, String type, String ticker, String quantity) throws Exception {
        String json = """
                {"accountId":"%s","type":"%s","date":"2026-01-01","ticker":"%s","quantity":"%s","price":"100","affectsBalance":true}"""
                .formatted(account, type, ticker, quantity);
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private ResultActions savePlan(String token, String account, String json) throws Exception {
        return as(token, put("/api/v1/positions/%s/PAS/plan".formatted(account))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions details(String token, String account) throws Exception {
        return as(token, get("/api/v1/positions/%s/PAS/details".formatted(account)));
    }

    private void describeCompany(String json) throws Exception {
        String body = as(anna, get("/api/v1/instruments")).andReturn().getResponse().getContentAsString();
        as(anna, put("/api/v1/instruments/" + JsonPath.<String>read(body, "$[0].id"))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    /** Sets PAS's price directly, as a price fetch would. */
    private void price(String price) {
        jdbc.sql("UPDATE instruments SET quote_symbol = 'PASSUS', quote_currency = 'PLN' WHERE ticker = 'PAS'").update();
        jdbc.sql("""
                INSERT INTO quotes (symbol, price, quoted_at, fetched_at) VALUES ('PASSUS', ?, now(), now())
                ON CONFLICT (symbol) DO UPDATE SET price = excluded.price""").param(new java.math.BigDecimal(price)).update();
    }

    @Test
    void eachAccountHasItsOwnPlanForTheSameCompany() throws Exception {
        savePlan(anna, ike, "{\"plan\":\"Hold ten years\",\"risk\":2}").andExpect(status().isOk());
        savePlan(anna, brokerage, "{\"plan\":\"Trade earnings\",\"risk\":4,\"targetPrice\":\"180\",\"stopPrice\":\"120\"}")
                .andExpect(status().isOk());

        details(anna, ike).andExpect(jsonPath("$.plan").value("Hold ten years"))
                .andExpect(jsonPath("$.risk").value(2))
                .andExpect(jsonPath("$.targetPrice").doesNotExist());
        details(anna, brokerage).andExpect(jsonPath("$.plan").value("Trade earnings"))
                .andExpect(jsonPath("$.targetPrice").value("180"))
                .andExpect(jsonPath("$.stopPrice").value("120"));
    }

    @Test
    void theCompanyIsSharedAcrossAccounts() throws Exception {
        describeCompany("{\"name\":\"Passus\",\"sector\":\"IT\",\"risk\":3,\"thesis\":\"Demand\",\"analysis\":\"# Notes\"}");

        details(anna, ike).andExpect(jsonPath("$.name").value("Passus"))
                .andExpect(jsonPath("$.sector").value("IT"))
                .andExpect(jsonPath("$.companyRisk").value(3))
                .andExpect(jsonPath("$.thesis").value("Demand"))
                .andExpect(jsonPath("$.analysis").value("# Notes"));
        details(anna, brokerage).andExpect(jsonPath("$.name").value("Passus"));
    }

    @Test
    void aPositionWithoutAPlanHasAnEmptyOne() throws Exception {
        details(anna, ike).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value(""))
                .andExpect(jsonPath("$.risk").doesNotExist());
    }

    @Test
    void positionRiskIsThePlansOrElseTheCompanys() throws Exception {
        describeCompany("{\"risk\":3,\"sector\":\"IT\"}");
        savePlan(anna, brokerage, "{\"risk\":5}");

        as(anna, get("/api/v1/positions").param("account", ike))
                .andExpect(jsonPath("$[0].risk").value(3))
                .andExpect(jsonPath("$[0].sector").value("IT"));
        as(anna, get("/api/v1/positions").param("account", brokerage))
                .andExpect(jsonPath("$[0].risk").value(5));
    }

    @Test
    void clearingTheOverrideFallsBackToTheCompany() throws Exception {
        describeCompany("{\"risk\":3}");
        savePlan(anna, ike, "{\"risk\":5,\"plan\":\"x\"}");

        savePlan(anna, ike, "{\"plan\":\"x\"}");

        as(anna, get("/api/v1/positions").param("account", ike)).andExpect(jsonPath("$[0].risk").value(3));
    }

    @Test
    void noRiskAnywhereIsLeftOut() throws Exception {
        as(anna, get("/api/v1/positions").param("account", ike)).andExpect(jsonPath("$[0].risk").doesNotExist());
    }

    @Test
    void showsHowFarThePriceIsFromTheLevels() throws Exception {
        price("150");
        savePlan(anna, ike, "{\"targetPrice\":\"180\",\"stopPrice\":\"120\"}")
                .andExpect(jsonPath("$.targetDistance").value("20.0"))
                .andExpect(jsonPath("$.stopDistance").value("-20.0"))
                .andExpect(jsonPath("$.signal").doesNotExist());
    }

    @Test
    void signalsNearAndPastLevels() throws Exception {
        savePlan(anna, ike, "{\"targetPrice\":\"180\",\"stopPrice\":\"120\"}");

        price("175");
        as(anna, get("/api/v1/positions").param("account", ike)).andExpect(jsonPath("$[0].signal").value("NEAR_TARGET"));
        price("185");
        as(anna, get("/api/v1/positions").param("account", ike)).andExpect(jsonPath("$[0].signal").value("TARGET_REACHED"));
        price("110");
        as(anna, get("/api/v1/positions").param("account", ike)).andExpect(jsonPath("$[0].signal").value("STOP_HIT"));
    }

    @Test
    void noPriceMeansNoSignal() throws Exception {
        savePlan(anna, ike, "{\"targetPrice\":\"180\"}").andExpect(jsonPath("$.targetDistance").doesNotExist());
    }

    @Test
    void aClosedPositionKeepsItsPlanButHasNoSignal() throws Exception {
        price("175");
        savePlan(anna, ike, "{\"plan\":\"Sell at target\",\"targetPrice\":\"180\"}");

        trade(ike, "sell", "PAS", "10");

        details(anna, ike).andExpect(jsonPath("$.plan").value("Sell at target"))
                .andExpect(jsonPath("$.signal").doesNotExist());
    }

    @Test
    void levelsMustBePositive() throws Exception {
        savePlan(anna, ike, "{\"targetPrice\":\"-1\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetPrice"));
        savePlan(anna, ike, "{\"risk\":9}").andExpect(status().isBadRequest());
    }

    @Test
    void savingReplacesThePlan() throws Exception {
        savePlan(anna, ike, "{\"plan\":\"first\",\"targetPrice\":\"180\"}");

        savePlan(anna, ike, "{\"plan\":\"second\"}").andExpect(jsonPath("$.plan").value("second"))
                .andExpect(jsonPath("$.targetPrice").doesNotExist());
    }

    @Test
    void plansArePrivate() throws Exception {
        savePlan(anna, ike, "{\"plan\":\"secret\"}");

        details(bob, ike).andExpect(status().isNotFound());
        savePlan(bob, ike, "{\"plan\":\"mine\"}").andExpect(status().isNotFound());

        details(anna, ike).andExpect(jsonPath("$.plan").value("secret"));
    }

    @Test
    void thereIsNoPlanWithoutAPosition() throws Exception {
        String empty = createAccount("Empty");

        savePlan(anna, empty, "{\"plan\":\"x\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("position_not_found"));
    }

    @Test
    void deletingAnAccountDeletesItsPlans() throws Exception {
        savePlan(anna, ike, "{\"plan\":\"x\"}");

        as(anna, delete("/api/v1/accounts/" + ike)).andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(
                jdbc.sql("SELECT count(*) FROM position_plans").query(Long.class).single()).isZero();
    }

    @Test
    void deletingAUserWorksWithPlansInPlace() throws Exception {
        savePlan(anna, ike, "{\"plan\":\"x\"}");

        users.deleteAll();

        org.assertj.core.api.Assertions.assertThat(
                jdbc.sql("SELECT count(*) FROM position_plans").query(Long.class).single()).isZero();
    }
}
