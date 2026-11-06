package io.github.akarp0508.koifolio.price;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
class PriceTests {

    private static final Instant QUOTED = Instant.parse("2026-11-05T16:00:00Z");

    /** Stands in for the real source. Knows PAS (PLN) and AAPL (USD). */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeSource {

        @Bean
        @Primary
        FakeProvider fakeProvider() {
            return new FakeProvider();
        }
    }

    static class FakeProvider implements QuoteProvider {

        final Map<String, ResolvedSymbol> symbols = new ConcurrentHashMap<>();
        final Map<String, Quote> quotes = new ConcurrentHashMap<>();
        final Map<String, List<DailyClose>> closes = new ConcurrentHashMap<>();
        final List<String> historyCalls = new CopyOnWriteArrayList<>();
        volatile boolean failing;
        volatile int resolveCalls;

        @Override
        public Optional<ResolvedSymbol> resolve(String ticker) {
            resolveCalls++;
            if (failing) {
                throw new IllegalStateException("source is down");
            }
            return Optional.ofNullable(symbols.get(ticker));
        }

        @Override
        public List<DailyClose> dailyCloses(String symbol, String isin, LocalDate from, LocalDate to) {
            if (failing) {
                throw new IllegalStateException("source is down");
            }
            historyCalls.add(symbol + " " + from + " " + to);
            return closes.getOrDefault(symbol, List.of()).stream()
                    .filter(close -> !close.date().isBefore(from) && close.date().isBefore(to))
                    .toList();
        }

        @Override
        public Map<String, Quote> quotes(Collection<String> requested) {
            if (failing) {
                throw new IllegalStateException("source is down");
            }
            Map<String, Quote> found = new HashMap<>(quotes);
            found.keySet().retainAll(requested);
            return found;
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FakeProvider source;

    private String anna;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        jdbc.sql("DELETE FROM quotes").update();
        jdbc.sql("DELETE FROM daily_closes").update();
        jdbc.sql("DELETE FROM price_history").update();
        source.failing = false;
        source.resolveCalls = 0;
        source.symbols.clear();
        source.closes.clear();
        source.historyCalls.clear();
        source.quotes.clear();
        source.symbols.put("PAS", new QuoteProvider.ResolvedSymbol("PASSUS", "PLISIN", "PLN"));
        source.symbols.put("AAPL", new QuoteProvider.ResolvedSymbol("APPLE", null, "USD"));
        source.quotes.put("PASSUS", new QuoteProvider.Quote(new BigDecimal("151"), QUOTED));
        source.quotes.put("APPLE", new QuoteProvider.Quote(new BigDecimal("344.17"), QUOTED));
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

    private void buy(String token, String ticker, String currency, String quantity, String price) throws Exception {
        String account = as(token, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"currency\":\"%s\",\"balance\":\"1000000\"}".formatted(currency)))
                .andReturn().getResponse().getContentAsString();
        String json = """
                {"accountId":"%s","type":"buy","date":"2026-01-01","ticker":"%s","quantity":"%s","price":"%s","affectsBalance":true}"""
                .formatted(JsonPath.<String>read(account, "$.id"), ticker, quantity, price);
        as(token, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private void refresh() throws Exception {
        as(anna, post("/api/v1/prices/refresh")).andExpect(status().isNoContent());
    }

    @Test
    void positionGetsItsPriceValueAndGain() throws Exception {
        buy(anna, "PAS", "PLN", "15", "110");

        refresh();

        as(anna, get("/api/v1/positions"))
                .andExpect(jsonPath("$[0].price").value("151"))
                .andExpect(jsonPath("$[0].priceCurrency").value("PLN"))
                .andExpect(jsonPath("$[0].quotedAt").value("2026-11-05T16:00:00Z"))
                .andExpect(jsonPath("$[0].value").value("2265.00"))
                .andExpect(jsonPath("$[0].unrealizedGain").value("615.00"))
                .andExpect(jsonPath("$[0].unrealizedPercent").value("37.3"))
                .andExpect(jsonPath("$[0].currencyMismatch").value(false));
    }

    @Test
    void beforeTheFirstFetchThereIsNoPrice() throws Exception {
        buy(anna, "PAS", "PLN", "15", "110");

        as(anna, get("/api/v1/positions"))
                .andExpect(jsonPath("$[0].cost").value("1650.00"))
                .andExpect(jsonPath("$[0].price").doesNotExist())
                .andExpect(jsonPath("$[0].value").doesNotExist());
    }

    @Test
    void uncoveredTickerStaysWithoutAPrice() throws Exception {
        buy(anna, "OBSCURE", "PLN", "1", "10");

        refresh();

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].price").doesNotExist());
    }

    @Test
    void uncoveredTickerIsNotAskedAboutAgainStraightAway() throws Exception {
        buy(anna, "OBSCURE", "PLN", "1", "10");

        refresh();
        refresh();

        org.assertj.core.api.Assertions.assertThat(source.resolveCalls).isEqualTo(1);
    }

    @Test
    void foundSymbolIsNotLookedUpAgain() throws Exception {
        buy(anna, "PAS", "PLN", "1", "10");

        refresh();
        refresh();

        org.assertj.core.api.Assertions.assertThat(source.resolveCalls).isEqualTo(1);
    }

    @Test
    void priceInAnotherCurrencyShowsButIsNotValued() throws Exception {
        buy(anna, "AAPL", "PLN", "2", "1000");

        refresh();

        as(anna, get("/api/v1/positions"))
                .andExpect(jsonPath("$[0].price").value("344.17"))
                .andExpect(jsonPath("$[0].priceCurrency").value("USD"))
                .andExpect(jsonPath("$[0].currencyMismatch").value(true))
                .andExpect(jsonPath("$[0].value").doesNotExist())
                .andExpect(jsonPath("$[0].unrealizedGain").doesNotExist());
    }

    @Test
    void matchingForeignAccountIsValued() throws Exception {
        buy(anna, "AAPL", "USD", "2", "300");

        refresh();

        as(anna, get("/api/v1/positions"))
                .andExpect(jsonPath("$[0].value").value("688.34"))
                .andExpect(jsonPath("$[0].currencyMismatch").value(false));
    }

    @Test
    void closedPositionHasNoValue() throws Exception {
        buy(anna, "PAS", "PLN", "1", "100");
        String account = JsonPath.read(as(anna, get("/api/v1/accounts")).andReturn().getResponse().getContentAsString(), "$[0].id");
        String sell = """
                {"accountId":"%s","type":"sell","date":"2026-02-01","ticker":"PAS","quantity":"1","price":"150","affectsBalance":true}"""
                .formatted(account);
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(sell));

        refresh();

        as(anna, get("/api/v1/positions").param("closed", "true"))
                .andExpect(jsonPath("$[0].price").doesNotExist())
                .andExpect(jsonPath("$[0].value").doesNotExist());
    }

    @Test
    void aPriceIsSharedButPositionsStayPrivate() throws Exception {
        buy(anna, "PAS", "PLN", "1", "100");
        buy(bob, "PAS", "PLN", "2", "100");

        refresh();

        org.assertj.core.api.Assertions.assertThat(source.resolveCalls).isEqualTo(1);
        as(bob, get("/api/v1/positions")).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].value").value("302.00"));
    }

    @Test
    void aFailedFetchKeepsTheLastPrices() throws Exception {
        buy(anna, "PAS", "PLN", "1", "100");
        refresh();

        source.failing = true;
        source.quotes.put("PASSUS", new QuoteProvider.Quote(new BigDecimal("1"), QUOTED));
        refresh();

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].price").value("151"));
    }

    @Test
    void newPriceReplacesTheOldOne() throws Exception {
        buy(anna, "PAS", "PLN", "1", "100");
        refresh();

        source.quotes.put("PASSUS", new QuoteProvider.Quote(new BigDecimal("160.5"), QUOTED.plusSeconds(900)));
        refresh();

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].price").value("160.5"))
                .andExpect(jsonPath("$[0].quotedAt").value("2026-11-05T16:15:00Z"));
    }

    @Test
    void refreshNeedsAToken() throws Exception {
        mvc.perform(post("/api/v1/prices/refresh")).andExpect(status().isUnauthorized());
    }
}
