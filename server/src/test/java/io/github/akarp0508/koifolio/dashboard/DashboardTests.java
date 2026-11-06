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
class DashboardTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private String anna;
    private String bob;
    private String ike;
    private String usd;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        jdbc.sql("DELETE FROM quotes").update();
        jdbc.sql("DELETE FROM fx_rates").update();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
        ike = createAccount("IKE", "PLN", "1000");
        usd = createAccount("US", "USD", "100");
        rate("USD", "4");
        rate("EUR", "5");
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

    private String createAccount(String name, String currency, String balance) throws Exception {
        String body = as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"currency\":\"%s\",\"balance\":\"%s\"}".formatted(name, currency, balance)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void rate(String currency, String rate) {
        jdbc.sql("INSERT INTO fx_rates VALUES (?, ?, current_date, now())").params(currency, new BigDecimal(rate)).update();
    }

    /** Buys without touching the balance, and sets the price as a fetch would. */
    private void hold(String account, String ticker, String quantity, String cost, String price, String priceCurrency)
            throws Exception {
        String json = """
                {"accountId":"%s","type":"buy","date":"2026-01-01","ticker":"%s","quantity":"%s","price":"%s","affectsBalance":false}"""
                .formatted(account, ticker, quantity, cost);
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        if (price != null) {
            jdbc.sql("UPDATE instruments SET quote_symbol = ?, quote_currency = ? WHERE ticker = ?")
                    .params(ticker, priceCurrency, ticker).update();
            jdbc.sql("""
                    INSERT INTO quotes VALUES (?, ?, now(), now())
                    ON CONFLICT (symbol) DO UPDATE SET price = excluded.price""").params(ticker, new BigDecimal(price)).update();
        }
    }

    private String instrumentId(String ticker) {
        return jdbc.sql("SELECT id::text FROM instruments WHERE ticker = ?").param(ticker).query(String.class).single();
    }

    private void describe(String ticker, String json) throws Exception {
        as(anna, put("/api/v1/instruments/" + instrumentId(ticker)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private void plan(String account, String ticker, String json) throws Exception {
        as(anna, put("/api/v1/positions/%s/%s/plan".formatted(account, ticker))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    private ResultActions dashboard(String... params) throws Exception {
        var request = get("/api/v1/dashboard");
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return as(anna, request);
    }

    @Test
    void convertsEverythingToTheBaseCurrency() throws Exception {
        hold(ike, "PAS", "10", "100", "120", "PLN");
        hold(usd, "AAPL", "2", "50", "100", "USD");

        // Positions: 1200 PLN + 200 USD = 2000. Cash: 1000 PLN + 100 USD = 1400.
        dashboard().andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.positionsValue").value("2000.00"))
                .andExpect(jsonPath("$.cash").value("1400.00"))
                .andExpect(jsonPath("$.value").value("3400.00"))
                // Gain: 200 PLN + 100 USD = 600, on a cost of 1000 + 400
                .andExpect(jsonPath("$.unrealizedGain").value("600.00"))
                .andExpect(jsonPath("$.unrealizedPercent").value("42.9"))
                .andExpect(jsonPath("$.unpriced").value(0));
    }

    @Test
    void aForeignPriceInAPlnAccountIsNowValued() throws Exception {
        hold(ike, "AAPL", "2", "100", "100", "USD");

        as(anna, get("/api/v1/positions"))
                .andExpect(jsonPath("$[0].value").value("800.00"))
                .andExpect(jsonPath("$[0].unrealizedGain").value("600.00"))
                .andExpect(jsonPath("$[0].currencyMismatch").value(false));
    }

    @Test
    void aMissingRateStaysBlankAndCountsAsUnpriced() throws Exception {
        hold(ike, "VOD", "1", "10", "5", "GBP");

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].currencyMismatch").value(true))
                .andExpect(jsonPath("$[0].value").doesNotExist());
        dashboard().andExpect(jsonPath("$.unpriced").value(1))
                .andExpect(jsonPath("$.positionsValue").value("0.00"));
    }

    @Test
    void positionsWithoutAPriceAreCounted() throws Exception {
        hold(ike, "OBSCURE", "1", "10", null, null);

        dashboard().andExpect(jsonPath("$.unpriced").value(1));
    }

    @Test
    void baseCurrencyCanBeChanged() throws Exception {
        hold(ike, "PAS", "10", "100", "120", "PLN");

        as(anna, patch("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content("{\"baseCurrency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.currencies[0]").value("EUR"));

        dashboard().andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.positionsValue").value("300.00"));
    }

    @Test
    void crossRatesGoThroughPln() throws Exception {
        hold(usd, "AAPL", "1", "1", "10", "USD");
        as(anna, patch("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content("{\"baseCurrency\":\"EUR\"}"));

        dashboard().andExpect(jsonPath("$.positionsValue").value("8.00"));
    }

    @Test
    void aBaseCurrencyNeedsARate() throws Exception {
        as(anna, patch("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content("{\"baseCurrency\":\"XYZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unknown_currency"));
    }

    @Test
    void realizedGainsCountFromClosedPositionsToo() throws Exception {
        hold(usd, "AAPL", "1", "10", "20", "USD");
        String sell = """
                {"accountId":"%s","type":"sell","date":"2026-02-01","ticker":"AAPL","quantity":"1","price":"30","affectsBalance":false}"""
                .formatted(usd);
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(sell));

        dashboard().andExpect(jsonPath("$.realizedGain").value("80.00"))
                .andExpect(jsonPath("$.positionsValue").value("0.00"));
    }

    @Test
    void accountFilterAppliesToPositionsAndCash() throws Exception {
        hold(ike, "PAS", "10", "100", "120", "PLN");
        hold(usd, "AAPL", "2", "50", "100", "USD");

        dashboard("account", ike).andExpect(jsonPath("$.positionsValue").value("1200.00"))
                .andExpect(jsonPath("$.cash").value("1000.00"));
    }

    @Test
    void instrumentAndPositionTagsFilterIndependently() throws Exception {
        hold(ike, "PAS", "10", "100", "120", "PLN");
        hold(ike, "XTB", "1", "10", "10", "PLN");
        hold(usd, "PAS", "1", "1", "120", "PLN");
        describe("PAS", "{\"tags\":[\"Dividend\",\"polish\"]}");
        plan(ike, "PAS", "{\"tags\":[\"Core\"]}");
        plan(ike, "XTB", "{\"tags\":[\"core\"]}");

        dashboard("instrumentTag", "dividend").andExpect(jsonPath("$.positionsValue").value("1320.00"));
        dashboard("positionTag", "core").andExpect(jsonPath("$.positionsValue").value("1210.00"));
        dashboard("instrumentTag", "dividend", "positionTag", "core")
                .andExpect(jsonPath("$.positionsValue").value("1200.00"));
    }

    @Test
    void severalTagsOfOneKindMatchAny() throws Exception {
        hold(ike, "PAS", "1", "1", "10", "PLN");
        hold(ike, "XTB", "1", "1", "20", "PLN");
        hold(ike, "PKO", "1", "1", "40", "PLN");
        describe("PAS", "{\"tags\":[\"a\"]}");
        describe("XTB", "{\"tags\":[\"b\"]}");

        dashboard("instrumentTag", "a", "instrumentTag", "b").andExpect(jsonPath("$.positionsValue").value("30.00"));
    }

    @Test
    void cashIsLeftOutWhileTagsFilter() throws Exception {
        hold(ike, "PAS", "1", "1", "10", "PLN");
        describe("PAS", "{\"tags\":[\"a\"]}");

        dashboard("instrumentTag", "a").andExpect(jsonPath("$.cash").doesNotExist())
                .andExpect(jsonPath("$.value").value("10.00"));
    }

    @Test
    void breaksDownBySectorAndRisk() throws Exception {
        hold(ike, "PAS", "1", "1", "60", "PLN");
        hold(ike, "PKO", "1", "1", "30", "PLN");
        hold(ike, "XTB", "1", "1", "10", "PLN");
        describe("PAS", "{\"sector\":\"IT\",\"risk\":4}");
        describe("PKO", "{\"sector\":\"Banks\",\"risk\":2}");
        plan(ike, "PKO", "{\"risk\":5}");

        dashboard().andExpect(jsonPath("$.bySector.length()").value(3))
                .andExpect(jsonPath("$.bySector[0].label").value("IT"))
                .andExpect(jsonPath("$.bySector[0].percent").value("60.0"))
                .andExpect(jsonPath("$.bySector[2].label").doesNotExist())
                // PAS at 4 and PKO overridden to 5 share a group
                .andExpect(jsonPath("$.byRisk[0].label").value("4-5"))
                .andExpect(jsonPath("$.byRisk[0].percent").value("90.0"))
                .andExpect(jsonPath("$.byRisk[1].label").doesNotExist());
    }

    @Test
    void listsPositionsNeedingAttention() throws Exception {
        hold(ike, "PAS", "1", "1", "175", "PLN");
        hold(ike, "XTB", "1", "1", "100", "PLN");
        hold(ike, "PKO", "1", "1", "100", "PLN");
        plan(ike, "PAS", "{\"targetPrice\":\"180\"}");
        plan(ike, "XTB", "{\"stopPrice\":\"120\"}");
        plan(ike, "PKO", "{\"targetPrice\":\"500\"}");

        dashboard().andExpect(jsonPath("$.attention.length()").value(2))
                .andExpect(jsonPath("$.attention[0].ticker").value("PAS"))
                .andExpect(jsonPath("$.attention[0].accountName").value("IKE"))
                .andExpect(jsonPath("$.attention[0].signal").value("NEAR_TARGET"))
                .andExpect(jsonPath("$.attention[0].distance").value("2.9"))
                .andExpect(jsonPath("$.attention[1].signal").value("STOP_HIT"))
                .andExpect(jsonPath("$.attention[1].distance").value("20.0"));
    }

    @Test
    void tagsAreCleanedAndShownOnPositions() throws Exception {
        hold(ike, "PAS", "1", "1", "10", "PLN");
        describe("PAS", "{\"tags\":[\" Dividend \",\"dividend\",\"\"]}");
        plan(ike, "PAS", "{\"tags\":[\"Core\"]}");

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].instrumentTags.length()").value(1))
                .andExpect(jsonPath("$[0].instrumentTags[0]").value("dividend"))
                .andExpect(jsonPath("$[0].positionTags[0]").value("core"));
    }

    @Test
    void savingAPlanWithoutTagsClearsThem() throws Exception {
        hold(ike, "PAS", "1", "1", "10", "PLN");
        plan(ike, "PAS", "{\"tags\":[\"core\"]}");

        plan(ike, "PAS", "{\"plan\":\"x\"}");

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].positionTags.length()").value(0));
    }

    @Test
    void positionsShowTheirWeightsWithCashCounted() throws Exception {
        hold(ike, "PAS", "10", "100", "300", "PLN");
        hold(usd, "AAPL", "1", "50", "100", "USD");

        as(anna, get("/api/v1/positions")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].weight").value("50.0"))
                .andExpect(jsonPath("$[0].portfolioWeight").value("8.3"))
                .andExpect(jsonPath("$[1].weight").value("75.0"))
                .andExpect(jsonPath("$[1].portfolioWeight").value("62.5"));
    }

    @Test
    void filteringPositionsKeepsTheirPortfolioWeight() throws Exception {
        hold(ike, "PAS", "10", "100", "300", "PLN");
        hold(usd, "AAPL", "1", "50", "100", "USD");

        as(anna, get("/api/v1/positions").param("account", ike))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].portfolioWeight").value("62.5"));
    }

    @Test
    void positionsWithoutAValueHaveNoWeight() throws Exception {
        hold(ike, "ABC", "10", "100", null, null);
        hold(ike, "PAS", "10", "100", "100", "PLN");

        as(anna, get("/api/v1/positions")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weight").doesNotExist())
                .andExpect(jsonPath("$[1].weight").value("50.0"));
    }

    @Test
    void aMissingRateLeavesOnlyTheAccountWeight() throws Exception {
        String chf = createAccount("CH", "CHF", "100");
        hold(chf, "NESN", "1", "100", "100", "CHF");

        as(anna, get("/api/v1/positions")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weight").value("50.0"))
                .andExpect(jsonPath("$[0].portfolioWeight").doesNotExist());
    }

    @Test
    void holdingsAddUpACompanyAcrossAccountsBiggestFirst() throws Exception {
        String other = createAccount("XTB", "PLN", "0");
        hold(ike, "AAA", "1", "50", "100", "PLN");
        hold(ike, "PAS", "10", "100", "300", "PLN");
        hold(other, "PAS", "5", "100", "300", "PLN");

        dashboard().andExpect(jsonPath("$.holdings[0].ticker").value("PAS"))
                .andExpect(jsonPath("$.holdings[0].held").value(true))
                .andExpect(jsonPath("$.holdings[0].value").value("4500.00"))
                .andExpect(jsonPath("$.holdings[0].unrealizedGain").value("3000.00"))
                .andExpect(jsonPath("$.holdings[0].unrealizedPercent").value("200.0"))
                .andExpect(jsonPath("$.holdings[0].accounts.length()").value(2))
                .andExpect(jsonPath("$.holdings[0].accounts[0].value").value("3000.00"))
                .andExpect(jsonPath("$.holdings[1].ticker").value("AAA"));
    }

    @Test
    void holdingWeightIsOfThePortfolioOrOfTheChosenAccount() throws Exception {
        hold(ike, "PAS", "10", "100", "300", "PLN");
        hold(usd, "AAPL", "1", "50", "100", "USD");

        dashboard().andExpect(jsonPath("$.holdings[0].ticker").value("PAS"))
                .andExpect(jsonPath("$.holdings[0].weight").value("62.5"))
                .andExpect(jsonPath("$.holdings[1].weight").value("8.3"));
        dashboard("account", ike).andExpect(jsonPath("$.holdings.length()").value(1))
                .andExpect(jsonPath("$.holdings[0].weight").value("75.0"));
    }

    @Test
    void holdingsAreInTheBaseCurrency() throws Exception {
        hold(usd, "AAPL", "1", "50", "100", "USD");

        dashboard().andExpect(jsonPath("$.holdings[0].value").value("400.00"))
                .andExpect(jsonPath("$.holdings[0].accounts[0].accountName").value("US"));
    }

    @Test
    void tagsNarrowHoldingsButNotTheirWeight() throws Exception {
        String other = createAccount("XTB", "PLN", "0");
        hold(ike, "PAS", "10", "100", "300", "PLN");
        hold(other, "PAS", "5", "100", "300", "PLN");
        plan(ike, "PAS", "{\"tags\":[\"core\"]}");

        dashboard("positionTag", "core").andExpect(jsonPath("$.holdings[0].accounts.length()").value(1))
                .andExpect(jsonPath("$.holdings[0].value").value("3000.00"))
                .andExpect(jsonPath("$.holdings[0].weight").value("50.8"));
    }

    @Test
    void aSoldCompanyIsNotHeldAndKeepsItsRealizedGain() throws Exception {
        hold(ike, "PAS", "10", "100", "300", "PLN");
        String json = """
                {"accountId":"%s","type":"sell","date":"2026-02-01","ticker":"PAS","quantity":"10","price":"120","affectsBalance":false}"""
                .formatted(ike);
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());

        dashboard().andExpect(jsonPath("$.holdings[0].held").value(false))
                .andExpect(jsonPath("$.holdings[0].realizedGain").value("200.00"))
                .andExpect(jsonPath("$.holdings[0].value").doesNotExist())
                .andExpect(jsonPath("$.holdings[0].weight").doesNotExist())
                .andExpect(jsonPath("$.holdings[0].accounts[0].closed").value(true));
    }

    @Test
    void anUnpricedHoldingComesLast() throws Exception {
        hold(ike, "AAA", "1", "50", null, null);
        hold(ike, "PAS", "1", "50", "10", "PLN");

        dashboard().andExpect(jsonPath("$.holdings[0].ticker").value("PAS"))
                .andExpect(jsonPath("$.holdings[1].ticker").value("AAA"))
                .andExpect(jsonPath("$.holdings[1].value").doesNotExist());
    }

    @Test
    void aNewUserHasAnEmptyDashboard() throws Exception {
        as(bob, get("/api/v1/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("0.00"))
                .andExpect(jsonPath("$.unrealizedPercent").doesNotExist())
                .andExpect(jsonPath("$.bySector.length()").value(0));
    }

    @Test
    void dashboardsArePrivate() throws Exception {
        hold(ike, "PAS", "1", "1", "10", "PLN");

        as(bob, get("/api/v1/dashboard")).andExpect(jsonPath("$.positionsValue").value("0.00"));
        as(bob, get("/api/v1/dashboard").param("account", ike)).andExpect(jsonPath("$.cash").value("0.00"));
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/settings")).andExpect(status().isUnauthorized());
    }
}
