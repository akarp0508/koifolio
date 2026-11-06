package io.github.akarp0508.koifolio.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import io.github.akarp0508.koifolio.domain.DailyClose;
import io.github.akarp0508.koifolio.price.FakePrices;
import io.github.akarp0508.koifolio.price.PriceHistoryService;
import java.math.BigDecimal;
import java.sql.Date;
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
@Import({TestDatabase.class, FakePrices.Config.class})
class SplitSuggestionTests {

    private static final LocalDate TODAY = LocalDate.now(PriceHistoryService.MARKET_ZONE);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FakePrices prices;

    private String anna;
    private String ike;
    private String brokerage;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        for (String table : List.of("split_suggestions", "daily_closes", "price_history", "quotes")) {
            jdbc.sql("DELETE FROM " + table).update();
        }
        prices.reset();
        prices.symbol("PAS", "PASSUS");
        prices.closes("PASSUS", closesFrom(60, "500"));
        anna = tokenFor("anna");
        ike = createAccount("IKE");
        brokerage = createAccount("Brokerage");
    }

    /** One close a day up to yesterday. */
    private static List<DailyClose> closesFrom(int daysAgo, String price) {
        List<DailyClose> closes = new ArrayList<>();
        for (int day = daysAgo; day >= 1; day--) {
            closes.add(new DailyClose(TODAY.minusDays(day), new BigDecimal(price)));
        }
        return closes;
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
                .content("{\"name\":\"%s\",\"currency\":\"PLN\",\"balance\":\"0\"}".formatted(name)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void buy(String account) throws Exception {
        String json = """
                {"accountId":"%s","type":"buy","date":"%s","ticker":"PAS","quantity":"10","price":"500","affectsBalance":false}"""
                .formatted(account, TODAY.minusDays(40));
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private void refresh() throws Exception {
        as(anna, post("/api/v1/prices/refresh")).andExpect(status().isNoContent());
    }

    /** The stock splits 10 for 1 today: the source rewrites its past, and it's a new day for us. */
    private void splitHappens() throws Exception {
        prices.closes("PASSUS", closesFrom(60, "50"));
        jdbc.sql("DELETE FROM daily_closes WHERE date >= ?").param(Date.valueOf(TODAY.minusDays(2))).update();
        jdbc.sql("UPDATE price_history SET checked_on = ?").param(Date.valueOf(TODAY.minusDays(2))).update();
        refresh();
    }

    private ResultActions suggestions() throws Exception {
        return as(anna, get("/api/v1/split-suggestions"));
    }

    private String suggestionId() throws Exception {
        return JsonPath.read(suggestions().andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    private BigDecimal storedClose(int daysAgo) {
        return jdbc.sql("SELECT close FROM daily_closes WHERE date = ?").param(Date.valueOf(TODAY.minusDays(daysAgo)))
                .query(BigDecimal.class).single();
    }

    @Test
    void nothingIsSuggestedWithoutASplit() throws Exception {
        buy(ike);
        refresh();

        suggestions().andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aRewrittenPastRefreshesEvenOldClosesBeyondTheUsualWindow() throws Exception {
        buy(ike);
        refresh();

        splitHappens();

        assertThat(storedClose(39)).isEqualByComparingTo("50");
        assertThat(storedClose(3)).isEqualByComparingTo("50");
    }

    @Test
    void suggestsTheSplitAroundTheFirstNewDay() throws Exception {
        buy(ike);
        buy(brokerage);
        refresh();

        splitHappens();

        suggestions().andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticker").value("PAS"))
                .andExpect(jsonPath("$[0].splitFrom").value(1))
                .andExpect(jsonPath("$[0].splitTo").value(10))
                .andExpect(jsonPath("$[0].date").value(TODAY.minusDays(2).toString()))
                .andExpect(jsonPath("$[0].accounts.length()").value(2));
    }

    @Test
    void nothingChangesUntilTheUserAddsIt() throws Exception {
        buy(ike);
        refresh();

        splitHappens();

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].quantity").value("10"));
    }

    @Test
    void addingItEntersTheSplitInEveryAffectedAccount() throws Exception {
        buy(ike);
        buy(brokerage);
        refresh();
        splitHappens();

        as(anna, post("/api/v1/split-suggestions/" + suggestionId() + "/apply")).andExpect(status().isNoContent());

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].quantity").value("100"))
                .andExpect(jsonPath("$[1].quantity").value("100"));
        suggestions().andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void afterAddingItPastValuesAreRight() throws Exception {
        buy(ike);
        refresh();
        splitHappens();

        as(anna, post("/api/v1/split-suggestions/" + suggestionId() + "/apply")).andExpect(status().isNoContent());

        as(anna, get("/api/v1/positions/%s/PAS/history".formatted(ike)))
                .andExpect(jsonPath("$.points[0].value").value("5000.00"))
                .andExpect(jsonPath("$.points[0].close").value("500"));
    }

    @Test
    void dismissingHidesItForThatUser() throws Exception {
        buy(ike);
        refresh();
        splitHappens();

        as(anna, post("/api/v1/split-suggestions/" + suggestionId() + "/dismiss")).andExpect(status().isNoContent());

        suggestions().andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aSplitAlreadyEnteredIsNotSuggested() throws Exception {
        buy(ike);
        refresh();
        String split = """
                {"accountId":"%s","type":"split","date":"%s","ticker":"PAS","splitFrom":"1","splitTo":"10","affectsBalance":false}"""
                .formatted(ike, TODAY.minusDays(5));
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(split))
                .andExpect(status().isCreated());

        splitHappens();

        suggestions().andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anAccountThatSoldOutBeforehandIsNotAffected() throws Exception {
        buy(ike);
        buy(brokerage);
        String sell = """
                {"accountId":"%s","type":"sell","date":"%s","ticker":"PAS","quantity":"10","price":"500","affectsBalance":false}"""
                .formatted(brokerage, TODAY.minusDays(20));
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(sell));
        refresh();

        splitHappens();

        suggestions().andExpect(jsonPath("$[0].accounts.length()").value(1))
                .andExpect(jsonPath("$[0].accounts[0]").value("IKE"));
    }

    @Test
    void anotherUserDoesNotSeeIt() throws Exception {
        buy(ike);
        refresh();
        splitHappens();
        String bob = tokenFor("bob");

        as(bob, get("/api/v1/split-suggestions")).andExpect(jsonPath("$.length()").value(0));
        as(bob, post("/api/v1/split-suggestions/" + suggestionId() + "/apply")).andExpect(status().isNotFound());
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/v1/split-suggestions")).andExpect(status().isUnauthorized());
    }
}
