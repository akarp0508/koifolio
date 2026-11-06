package io.github.akarp0508.koifolio.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** A USD account, bought when USD was 4 and sold or valued at 5. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabase.class)
class PurchaseFxTests {

    private static final LocalDate TODAY = LocalDate.now(PriceHistoryService.MARKET_ZONE);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private String anna;
    private String usd;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        for (String table : java.util.List.of("quotes", "fx_rates", "fx_history", "daily_closes")) {
            jdbc.sql("DELETE FROM " + table).update();
        }
        String credentials = "{\"username\":\"anna\",\"password\":\"correct horse\"}";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andReturn().getResponse().getContentAsString();
        anna = JsonPath.read(body, "$.accessToken");
        String account = as(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"US\",\"currency\":\"USD\",\"balance\":\"0\"}"))
                .andReturn().getResponse().getContentAsString();
        usd = JsonPath.read(account, "$.id");
        jdbc.sql("INSERT INTO fx_rates VALUES ('USD', 5, current_date, now())").update();
        usdRate(31, "3");
        usdRate(30, "4");
    }

    private ResultActions as(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + anna));
    }

    private void usdRate(int daysAgo, String rate) {
        jdbc.sql("INSERT INTO fx_history VALUES ('USD', ?, ?)")
                .params(Date.valueOf(TODAY.minusDays(daysAgo)), new BigDecimal(rate)).update();
    }

    private void trade(String type, int daysAgo, String quantity, String price, String fee) throws Exception {
        String json = """
                {"accountId":"%s","type":"%s","date":"%s","ticker":"AAPL","quantity":"%s","price":"%s","fee":"%s","affectsBalance":false}"""
                .formatted(usd, type, TODAY.minusDays(daysAgo), quantity, price, fee);
        as(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private void price(String price) {
        jdbc.sql("UPDATE instruments SET quote_symbol = 'AAPL', quote_currency = 'USD'").update();
        jdbc.sql("INSERT INTO quotes VALUES ('AAPL', ?, now(), now())").param(new BigDecimal(price)).update();
    }

    private void useTransactionDate() throws Exception {
        as(patch("/api/v1/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"costFxRule\":\"transaction_date\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.costFxRule").value("transaction_date"))
                .andExpect(jsonPath("$.baseCurrency").value("PLN"));
    }

    @Test
    void costUsesTheLastRateBeforeTheTradeByDefault() throws Exception {
        trade("buy", 30, "1", "100", "0");
        price("100");

        // The rate before the trade was 3, so cost is 300 and value 500
        as(get("/api/v1/dashboard")).andExpect(jsonPath("$.positionsValue").value("500.00"))
                .andExpect(jsonPath("$.unrealizedGain").value("200.00"));
    }

    @Test
    void theRuleCanBeTheTradesOwnDate() throws Exception {
        useTransactionDate();
        trade("buy", 30, "1", "100", "0");
        price("120");

        // Cost 400, value 600: 100 from the stock and 100 from the currency
        as(get("/api/v1/dashboard")).andExpect(jsonPath("$.positionsValue").value("600.00"))
                .andExpect(jsonPath("$.unrealizedGain").value("200.00"))
                .andExpect(jsonPath("$.currencyGain").value("100.00"))
                .andExpect(jsonPath("$.unrealizedPercent").value("50.0"));
    }

    @Test
    void realizedGainUsesTheRateOfEachSide() throws Exception {
        useTransactionDate();
        usdRate(10, "5");
        trade("buy", 30, "1", "100", "0");
        trade("sell", 10, "1", "100", "0");

        // No gain in USD, but bought at 400 and sold at 500
        as(get("/api/v1/dashboard")).andExpect(jsonPath("$.realizedGain").value("100.00"));
    }

    @Test
    void feesConvertWithTheTrade() throws Exception {
        useTransactionDate();
        trade("buy", 30, "1", "100", "10");
        price("100");

        as(get("/api/v1/positions")).andExpect(jsonPath("$[0].inBase.cost").value("440.00"));
    }

    @Test
    void lotsKeepTheirOwnRates() throws Exception {
        useTransactionDate();
        usdRate(20, "4.5");
        trade("buy", 30, "1", "100", "0");
        trade("buy", 20, "1", "100", "0");
        price("100");

        as(get("/api/v1/positions")).andExpect(jsonPath("$[0].inBase.cost").value("850.00"));
    }

    @Test
    void positionsShowTheirBaseFiguresOnlyForAForeignAccount() throws Exception {
        useTransactionDate();
        trade("buy", 30, "1", "100", "0");
        price("120");

        as(get("/api/v1/positions")).andExpect(jsonPath("$[0].value").value("120.00"))
                .andExpect(jsonPath("$[0].inBase.currency").value("PLN"))
                .andExpect(jsonPath("$[0].inBase.value").value("600.00"))
                .andExpect(jsonPath("$[0].inBase.gain").value("200.00"))
                .andExpect(jsonPath("$[0].inBase.currencyGain").value("100.00"));

        as(patch("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content("{\"baseCurrency\":\"USD\"}"));
        as(get("/api/v1/positions")).andExpect(jsonPath("$[0].inBase").doesNotExist());
    }

    @Test
    void aSplitDoesNotChangeBaseCost() throws Exception {
        useTransactionDate();
        trade("buy", 30, "1", "100", "0");
        String split = """
                {"accountId":"%s","type":"split","date":"%s","ticker":"AAPL","splitFrom":"1","splitTo":"4","affectsBalance":false}"""
                .formatted(usd, TODAY.minusDays(5));
        as(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(split));
        price("30");

        as(get("/api/v1/positions")).andExpect(jsonPath("$[0].quantity").value("4"))
                .andExpect(jsonPath("$[0].inBase.cost").value("400.00"));
    }

    @Test
    void historyKeepsTheCostFixedWhileRatesMove() throws Exception {
        useTransactionDate();
        usdRate(10, "5");
        trade("buy", 30, "1", "100", "0");
        jdbc.sql("UPDATE instruments SET quote_symbol = 'AAPL', quote_currency = 'USD'").update();
        for (int daysAgo : new int[] {30, 10}) {
            jdbc.sql("INSERT INTO daily_closes VALUES ('AAPL', ?, 100)").param(Date.valueOf(TODAY.minusDays(daysAgo))).update();
        }

        as(get("/api/v1/dashboard/history")).andExpect(jsonPath("$.points[0].cost").value("400.00"))
                .andExpect(jsonPath("$.points[1].cost").value("400.00"))
                .andExpect(jsonPath("$.points[1].value").value("500.00"))
                .andExpect(jsonPath("$.points[1].gain").value("100.00"));
    }

    @Test
    void anAccountInTheBaseCurrencyIsUnaffected() throws Exception {
        as(patch("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content("{\"baseCurrency\":\"USD\"}"));
        trade("buy", 30, "1", "100", "0");
        price("120");

        as(get("/api/v1/dashboard")).andExpect(jsonPath("$.unrealizedGain").value("20.00"))
                .andExpect(jsonPath("$.currencyGain").value("0.00"));
    }
}
