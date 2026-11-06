package io.github.akarp0508.koifolio.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import io.github.akarp0508.koifolio.price.PriceHistoryService;
import java.math.BigDecimal;
import java.sql.Date;
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
class DashboardHistoryTests {

    private static final LocalDate TODAY = LocalDate.now(PriceHistoryService.MARKET_ZONE);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private String anna;
    private String bob;
    private String pln;
    private String usd;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        jdbc.sql("DELETE FROM quotes").update();
        jdbc.sql("DELETE FROM fx_rates").update();
        jdbc.sql("DELETE FROM fx_history").update();
        jdbc.sql("DELETE FROM daily_closes").update();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
        pln = createAccount("PLN");
        usd = createAccount("USD");
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

    private String createAccount(String currency) throws Exception {
        String body = as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"currency\":\"%s\",\"balance\":\"0\"}".formatted(currency, currency)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void buy(String account, String ticker, int daysAgo, String quantity, String price) throws Exception {
        String json = """
                {"accountId":"%s","type":"buy","date":"%s","ticker":"%s","quantity":"%s","price":"%s","affectsBalance":false}"""
                .formatted(account, TODAY.minusDays(daysAgo), ticker, quantity, price);
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private void priced(String ticker, String currency) {
        jdbc.sql("UPDATE instruments SET quote_symbol = ?, quote_currency = ? WHERE ticker = ?")
                .params(ticker, currency, ticker).update();
    }

    private void close(String ticker, int daysAgo, String close) {
        jdbc.sql("INSERT INTO daily_closes VALUES (?, ?, ?)")
                .params(ticker, Date.valueOf(TODAY.minusDays(daysAgo)), new BigDecimal(close)).update();
    }

    private void usdRate(int daysAgo, String rate) {
        jdbc.sql("INSERT INTO fx_history VALUES ('USD', ?, ?)")
                .params(Date.valueOf(TODAY.minusDays(daysAgo)), new BigDecimal(rate)).update();
    }

    private ResultActions history(String... params) throws Exception {
        var request = get("/api/v1/dashboard/history");
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return as(anna, request);
    }

    @Test
    void totalsPerDayAndChanges() throws Exception {
        buy(pln, "PAS", 3, "10", "100");
        priced("PAS", "PLN");
        close("PAS", 3, "100");
        close("PAS", 2, "110");
        close("PAS", 1, "120");

        history().andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.points.length()").value(3))
                .andExpect(jsonPath("$.points[2].value").value("1200.00"))
                .andExpect(jsonPath("$.points[2].cost").value("1000.00"))
                .andExpect(jsonPath("$.points[2].gain").value("200.00"));
    }

    @Test
    void aDayChangeComparesWithYesterday() throws Exception {
        buy(pln, "PAS", 5, "10", "100");
        priced("PAS", "PLN");
        close("PAS", 5, "100");
        close("PAS", 1, "120");
        close("PAS", 0, "126");

        history().andExpect(jsonPath("$.changes[0].period").value("DAY"))
                .andExpect(jsonPath("$.changes[0].change").value("60.00"))
                .andExpect(jsonPath("$.changes[0].percent").value("5.0"));
    }

    @Test
    void eachDayConvertsAtThatDaysRate() throws Exception {
        buy(usd, "AAPL", 2, "1", "100");
        priced("AAPL", "USD");
        close("AAPL", 2, "100");
        close("AAPL", 1, "100");
        usdRate(2, "4");
        usdRate(1, "5");

        history().andExpect(jsonPath("$.points[0].value").value("400.00"))
                .andExpect(jsonPath("$.points[1].value").value("500.00"));
    }

    @Test
    void aDayWithoutARateUsesTheLastPublishedOne() throws Exception {
        buy(usd, "AAPL", 2, "1", "100");
        priced("AAPL", "USD");
        close("AAPL", 2, "100");
        close("AAPL", 1, "100");
        usdRate(2, "4.5");

        history().andExpect(jsonPath("$.points[1].value").value("450.00"));
    }

    @Test
    void beforeFxHistoryArrivesTheLatestRateIsUsed() throws Exception {
        buy(usd, "AAPL", 1, "1", "100");
        priced("AAPL", "USD");
        close("AAPL", 1, "100");

        history().andExpect(jsonPath("$.points[0].value").value("400.00"));
    }

    @Test
    void aForeignStockInAPlnAccountConvertsEachClose() throws Exception {
        buy(pln, "AAPL", 2, "1", "400");
        priced("AAPL", "USD");
        close("AAPL", 2, "100");
        close("AAPL", 1, "100");
        usdRate(2, "4");
        usdRate(1, "5");

        as(anna, get("/api/v1/positions/%s/AAPL/history".formatted(pln)))
                .andExpect(jsonPath("$.currencyMismatch").value(false))
                .andExpect(jsonPath("$.points[1].value").value("500.00"));
    }

    @Test
    void aCurrencyWithNoRateIsLeftOut() throws Exception {
        buy(pln, "VOD", 1, "1", "10");
        priced("VOD", "GBP");
        close("VOD", 1, "5");

        history().andExpect(jsonPath("$.points.length()").value(0));
        as(anna, get("/api/v1/positions/%s/VOD/history".formatted(pln)))
                .andExpect(jsonPath("$.currencyMismatch").value(true))
                .andExpect(jsonPath("$.points[0].value").doesNotExist());
    }

    @Test
    void followsTheDashboardFilters() throws Exception {
        buy(pln, "PAS", 1, "1", "10");
        buy(usd, "AAPL", 1, "1", "10");
        priced("PAS", "PLN");
        priced("AAPL", "USD");
        close("PAS", 1, "10");
        close("AAPL", 1, "10");
        String id = jdbc.sql("SELECT id::text FROM instruments WHERE ticker = 'PAS'").query(String.class).single();
        as(anna, put("/api/v1/instruments/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"tags\":[\"a\"]}"));

        history("account", pln).andExpect(jsonPath("$.points[0].value").value("10.00"));
        history("instrumentTag", "a").andExpect(jsonPath("$.points[0].value").value("10.00"));
        history("instrument", id).andExpect(jsonPath("$.points[0].value").value("10.00"));
        history().andExpect(jsonPath("$.points[0].value").value("50.00"));
    }

    @Test
    void followsTheBaseCurrency() throws Exception {
        buy(pln, "PAS", 1, "1", "40");
        priced("PAS", "PLN");
        close("PAS", 1, "40");
        usdRate(1, "4");
        as(anna, patch("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content("{\"baseCurrency\":\"USD\"}"));

        history().andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.points[0].value").value("10.00"));
    }

    @Test
    void historyIsPrivate() throws Exception {
        buy(pln, "PAS", 1, "1", "10");
        priced("PAS", "PLN");
        close("PAS", 1, "10");

        as(bob, get("/api/v1/dashboard/history")).andExpect(jsonPath("$.points.length()").value(0));
    }

    @Test
    void anEmptyPortfolioHasAnEmptyHistory() throws Exception {
        history().andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(0))
                .andExpect(jsonPath("$.changes.length()").value(0));
    }
}
