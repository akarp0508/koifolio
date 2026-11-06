package io.github.akarp0508.koifolio.tax;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class TaxTests {

    private static final int THIS_YEAR = LocalDate.now().getYear();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private String anna;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        jdbc.sql("DELETE FROM quotes").update();
        jdbc.sql("DELETE FROM fx_rates").update();
        jdbc.sql("DELETE FROM fx_history").update();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
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

    private String account(String name, String extra) throws Exception {
        String body = as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"currency\":\"PLN\",\"balance\":\"0\"%s}".formatted(name, extra)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void trade(String account, String type, String date, String quantity, String price) throws Exception {
        String json = """
                {"accountId":"%s","type":"%s","date":"%s","ticker":"PAS","quantity":"%s","price":"%s","affectsBalance":false}"""
                .formatted(account, type, date, quantity, price);
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private void gain(String account, int year, String amount) throws Exception {
        trade(account, "buy", year + "-01-02", "1", "1000");
        trade(account, "sell", year + "-02-02", "1", new BigDecimal("1000").add(new BigDecimal(amount)).toPlainString());
    }

    private void price(String price) {
        jdbc.sql("UPDATE instruments SET quote_symbol = 'PAS', quote_currency = 'PLN'").update();
        jdbc.sql("INSERT INTO quotes VALUES ('PAS', ?, now(), now())").param(new BigDecimal(price)).update();
    }

    private ResultActions tax() throws Exception {
        return as(anna, get("/api/v1/tax"));
    }

    @Test
    void addsUpAccountsWithTheSameRate() throws Exception {
        gain(account("Brokerage", ",\"taxRate\":\"19\""), THIS_YEAR, "4000");
        gain(account("XTB", ",\"taxRate\":\"19\""), THIS_YEAR, "-500");

        tax().andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.year").value(THIS_YEAR))
                .andExpect(jsonPath("$.totalTax").value("665.00"))
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].ratePercent").value("19"))
                .andExpect(jsonPath("$.groups[0].realized").value("3500.00"))
                .andExpect(jsonPath("$.groups[0].accounts.length()").value(2))
                .andExpect(jsonPath("$.groups[0].accounts[1].realized").value("-500.00"));
    }

    @Test
    void aTaxFreeAccountIsItsOwnGroup() throws Exception {
        gain(account("IKE", ",\"taxRate\":\"0\""), THIS_YEAR, "2500");

        tax().andExpect(jsonPath("$.groups[0].ratePercent").value("0"))
                .andExpect(jsonPath("$.groups[0].realized").value("2500.00"))
                .andExpect(jsonPath("$.totalTax").value("0.00"));
    }

    @Test
    void anAccountWithNoRateIsNamedAndLeftOut() throws Exception {
        gain(account("Unset", ""), THIS_YEAR, "1000");

        tax().andExpect(jsonPath("$.unsetAccounts[0]").value("Unset"))
                .andExpect(jsonPath("$.groups.length()").value(0));
    }

    @Test
    void anAccountWithNothingToTaxIsLeftOut() throws Exception {
        account("Empty", "");

        tax().andExpect(jsonPath("$.unsetAccounts.length()").value(0));
    }

    @Test
    void eachYearStandsAlone() throws Exception {
        String account = account("A", ",\"taxRate\":\"19\"");
        gain(account, THIS_YEAR - 1, "-1000");
        gain(account, THIS_YEAR, "1000");

        tax().andExpect(jsonPath("$.totalTax").value("190.00"))
                .andExpect(jsonPath("$.years[0]").value(THIS_YEAR))
                .andExpect(jsonPath("$.years[1]").value(THIS_YEAR - 1));
        as(anna, get("/api/v1/tax").param("year", String.valueOf(THIS_YEAR - 1)))
                .andExpect(jsonPath("$.totalTax").value("0.00"))
                .andExpect(jsonPath("$.groups[0].realized").value("-1000.00"))
                .andExpect(jsonPath("$.totalIfSoldToday").doesNotExist())
                .andExpect(jsonPath("$.openPositions.length()").value(0));
    }

    @Test
    void ifSoldTodayCountsOpenPositions() throws Exception {
        String account = account("A", ",\"taxRate\":\"19\"");
        trade(account, "buy", THIS_YEAR + "-01-02", "10", "100");
        price("200");

        tax().andExpect(jsonPath("$.totalTax").value("0.00"))
                .andExpect(jsonPath("$.totalIfSoldToday").value("190.00"))
                .andExpect(jsonPath("$.groups[0].unrealized").value("1000.00"))
                .andExpect(jsonPath("$.openPositions[0].ticker").value("PAS"))
                .andExpect(jsonPath("$.openPositions[0].gain").value("1000.00"));
    }

    @Test
    void aForeignAccountUsesEachTradesRate() throws Exception {
        jdbc.sql("INSERT INTO fx_rates VALUES ('USD', 5, current_date, now())").update();
        String body = as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"US\",\"currency\":\"USD\",\"balance\":\"0\",\"taxRate\":\"19\"}"))
                .andReturn().getResponse().getContentAsString();
        String account = JsonPath.read(body, "$.id");
        jdbc.sql("INSERT INTO fx_history VALUES ('USD', ?, 4)").param(java.sql.Date.valueOf(THIS_YEAR + "-01-01")).update();
        jdbc.sql("INSERT INTO fx_history VALUES ('USD', ?, 5)").param(java.sql.Date.valueOf(THIS_YEAR + "-02-01")).update();
        trade(account, "buy", THIS_YEAR + "-01-02", "1", "100");
        trade(account, "sell", THIS_YEAR + "-02-02", "1", "100");

        // No gain in USD, but bought at 400 PLN and sold at 500 PLN
        tax().andExpect(jsonPath("$.groups[0].realized").value("100.00"))
                .andExpect(jsonPath("$.totalTax").value("19.00"));
    }

    @Test
    void theRateCanBeChangedAndCleared() throws Exception {
        String account = account("A", ",\"taxRate\":\"19\"");

        as(anna, patch("/api/v1/accounts/" + account).contentType(MediaType.APPLICATION_JSON).content("{\"taxRate\":\"0\"}"))
                .andExpect(jsonPath("$.taxRate").value("0"));
        as(anna, patch("/api/v1/accounts/" + account).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"B\"}"))
                .andExpect(jsonPath("$.taxRate").value("0"));
        as(anna, patch("/api/v1/accounts/" + account).contentType(MediaType.APPLICATION_JSON).content("{\"clearTaxRate\":true}"))
                .andExpect(jsonPath("$.taxRate").doesNotExist());
    }

    @Test
    void theRateMustBeAPercentage() throws Exception {
        as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"currency\":\"PLN\",\"taxRate\":\"101\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("taxRate"));
    }

    @Test
    void estimatesArePrivate() throws Exception {
        gain(account("A", ",\"taxRate\":\"19\""), THIS_YEAR, "1000");

        as(bob, get("/api/v1/tax")).andExpect(jsonPath("$.groups.length()").value(0));
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/v1/tax")).andExpect(status().isUnauthorized());
    }
}
