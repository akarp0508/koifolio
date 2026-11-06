package io.github.akarp0508.koifolio.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import io.github.akarp0508.koifolio.domain.DailyClose;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
@Import({TestDatabase.class, PriceTests.FakeSource.class})
class PriceHistoryTests {

    private static final LocalDate TODAY = LocalDate.now(PriceHistoryService.MARKET_ZONE);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PriceTests.FakeProvider source;

    private String anna;
    private String bob;
    private String account;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        jdbc.sql("DELETE FROM quotes").update();
        jdbc.sql("DELETE FROM daily_closes").update();
        jdbc.sql("DELETE FROM price_history").update();
        source.failing = false;
        source.symbols.clear();
        source.quotes.clear();
        source.closes.clear();
        source.historyCalls.clear();
        source.symbols.put("PAS", new QuoteProvider.ResolvedSymbol("PASSUS", "PLISIN", "PLN"));
        source.symbols.put("AAPL", new QuoteProvider.ResolvedSymbol("APPLE", null, "USD"));

        List<DailyClose> closes = new ArrayList<>();
        for (int daysAgo = 400; daysAgo >= 0; daysAgo--) {
            closes.add(new DailyClose(TODAY.minusDays(daysAgo), new BigDecimal(1000 - daysAgo)));
        }
        source.closes.put("PASSUS", closes);
        source.closes.put("APPLE", closes);

        anna = tokenFor("anna");
        bob = tokenFor("bob");
        account = createAccount(anna, "PLN");
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

    private String createAccount(String token, String currency) throws Exception {
        String body = as(token, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"currency\":\"%s\",\"balance\":\"10000000\"}".formatted(currency)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void buy(String token, String accountId, String ticker, int daysAgo) throws Exception {
        String json = """
                {"accountId":"%s","type":"buy","date":"%s","ticker":"%s","quantity":"2","price":"100","affectsBalance":false}"""
                .formatted(accountId, TODAY.minusDays(daysAgo), ticker);
        as(token, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private void refresh() throws Exception {
        as(anna, post("/api/v1/prices/refresh")).andExpect(status().isNoContent());
    }

    private ResultActions history(String token, String ticker) throws Exception {
        return as(token, get("/api/v1/positions/%s/%s/history".formatted(account, ticker)));
    }

    private long storedCloses() {
        return jdbc.sql("SELECT count(*) FROM daily_closes").query(Long.class).single();
    }

    @Test
    void fillsEveryDayFromTheFirstTradeWithoutTheUnfinishedToday() throws Exception {
        buy(anna, account, "PAS", 10);

        refresh();

        assertThat(storedCloses()).isEqualTo(10);
        history(anna, "PAS").andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("PAS"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.covered").value(true))
                .andExpect(jsonPath("$.points.length()").value(10))
                .andExpect(jsonPath("$.points[0].date").value(TODAY.minusDays(10).toString()))
                .andExpect(jsonPath("$.points[0].quantity").value("2"))
                .andExpect(jsonPath("$.points[0].close").value("990"))
                .andExpect(jsonPath("$.points[0].value").value("1980.00"))
                .andExpect(jsonPath("$.points[0].cost").value("200.00"))
                .andExpect(jsonPath("$.points[0].traded").value(true))
                .andExpect(jsonPath("$.points[1].traded").value(false));
    }

    @Test
    void todaysPriceIsAddedOnTopOfStoredCloses() throws Exception {
        buy(anna, account, "PAS", 3);
        source.quotes.put("PASSUS", new QuoteProvider.Quote(new BigDecimal("2000"), Instant.now()));

        refresh();

        history(anna, "PAS").andExpect(jsonPath("$.points.length()").value(4))
                .andExpect(jsonPath("$.points[3].date").value(TODAY.toString()))
                .andExpect(jsonPath("$.points[3].close").value("2000"));
    }

    @Test
    void anOldQuoteIsNotAddedAsANewDay() throws Exception {
        buy(anna, account, "PAS", 3);
        source.quotes.put("PASSUS", new QuoteProvider.Quote(new BigDecimal("2000"),
                TODAY.minusDays(1).atTime(12, 0).atZone(PriceHistoryService.MARKET_ZONE).toInstant()));

        refresh();

        history(anna, "PAS").andExpect(jsonPath("$.points.length()").value(3))
                .andExpect(jsonPath("$.points[2].close").value("999"));
    }

    @Test
    void aBackfilledOlderTradeFetchesTheEarlierDays() throws Exception {
        buy(anna, account, "PAS", 10);
        refresh();

        buy(anna, account, "PAS", 100);
        refresh();

        assertThat(storedCloses()).isEqualTo(100);
        history(anna, "PAS").andExpect(jsonPath("$.points.length()").value(100));
    }

    @Test
    void downtimeIsFilledInOnTheNextRun() throws Exception {
        buy(anna, account, "PAS", 30);
        refresh();
        // As if the service last ran 5 days ago
        jdbc.sql("DELETE FROM daily_closes WHERE date >= ?").param(java.sql.Date.valueOf(TODAY.minusDays(5))).update();
        jdbc.sql("UPDATE price_history SET checked_on = ?").param(java.sql.Date.valueOf(TODAY.minusDays(5))).update();

        refresh();

        assertThat(storedCloses()).isEqualTo(30);
    }

    @Test
    void anUpToDateSymbolIsNotFetchedAgainToday() throws Exception {
        buy(anna, account, "PAS", 10);

        refresh();
        refresh();

        assertThat(source.historyCalls).hasSize(1);
    }

    @Test
    void aDailyRunOnlyReReadsRecentDays() throws Exception {
        buy(anna, account, "PAS", 100);
        refresh();
        jdbc.sql("UPDATE price_history SET checked_on = ?").param(java.sql.Date.valueOf(TODAY.minusDays(1))).update();

        refresh();

        assertThat(source.historyCalls.getLast()).startsWith("PASSUS " + TODAY.minusDays(8));
    }

    @Test
    void aFailedFetchIsRetriedOnTheNextRun() throws Exception {
        buy(anna, account, "PAS", 10);
        refresh();
        jdbc.sql("DELETE FROM daily_closes").update();
        jdbc.sql("DELETE FROM price_history").update();
        source.failing = true;
        refresh();
        assertThat(storedCloses()).isZero();

        source.failing = false;
        refresh();

        assertThat(storedCloses()).isEqualTo(10);
    }

    @Test
    void closesAreSharedBetweenUsers() throws Exception {
        buy(anna, account, "PAS", 10);
        buy(bob, createAccount(bob, "PLN"), "PAS", 20);

        refresh();

        assertThat(source.historyCalls).hasSize(1);
        assertThat(storedCloses()).isEqualTo(20);
    }

    @Test
    void uncoveredTickerSaysSo() throws Exception {
        buy(anna, account, "OBSCURE", 3);

        refresh();

        history(anna, "OBSCURE").andExpect(status().isOk())
                .andExpect(jsonPath("$.covered").value(false))
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    @Test
    void anotherCurrencyHasClosesButNoValue() throws Exception {
        buy(anna, account, "AAPL", 3);

        refresh();

        history(anna, "AAPL").andExpect(jsonPath("$.currencyMismatch").value(true))
                .andExpect(jsonPath("$.points[0].close").value("997"))
                .andExpect(jsonPath("$.points[0].value").doesNotExist());
    }

    @Test
    void costMethodApplies() throws Exception {
        as(anna, patch("/api/v1/accounts/" + account).contentType(MediaType.APPLICATION_JSON)
                .content("{\"costMethod\":\"fifo\"}")).andExpect(status().isOk());
        buy(anna, account, "PAS", 3);

        refresh();

        history(anna, "PAS").andExpect(jsonPath("$.points[0].cost").value("200.00"));
    }

    @Test
    void tickerIgnoresCase() throws Exception {
        buy(anna, account, "PAS", 3);

        history(anna, "pas").andExpect(status().isOk());
    }

    @Test
    void historyIsPrivate() throws Exception {
        buy(anna, account, "PAS", 3);

        history(bob, "PAS").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("position_not_found"));
    }

    @Test
    void unknownPositionIsNotFound() throws Exception {
        history(anna, "NOPE").andExpect(status().isNotFound());
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/v1/positions/%s/PAS/history".formatted(account))).andExpect(status().isUnauthorized());
    }
}
