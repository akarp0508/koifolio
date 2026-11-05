package io.github.akarp0508.koifolio.position;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabase.class)
class PositionTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    private String anna;
    private String bob;
    private String account;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
        account = createAccount("A", "");
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

    private String createAccount(String name, String extra) throws Exception {
        String json = "{\"name\":\"%s\",\"currency\":\"PLN\",\"balance\":\"1000000\"%s}".formatted(name, extra);
        String body = as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void trade(String accountId, String type, String date, String ticker, String quantity, String price)
            throws Exception {
        String json = """
                {"accountId":"%s","type":"%s","date":"%s","ticker":"%s","quantity":"%s","price":"%s","affectsBalance":true}"""
                .formatted(accountId, type, date, ticker, quantity, price);
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void addsUpTradesWithTheAccountsDefaultAverageCost() throws Exception {
        trade(account, "buy", "2026-01-01", "PAS", "10", "100");
        trade(account, "buy", "2026-02-01", "PAS", "10", "120");
        trade(account, "sell", "2026-03-01", "PAS", "5", "150");

        as(anna, get("/api/v1/positions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountId").value(account))
                .andExpect(jsonPath("$[0].ticker").value("PAS"))
                .andExpect(jsonPath("$[0].currency").value("PLN"))
                .andExpect(jsonPath("$[0].quantity").value("15"))
                .andExpect(jsonPath("$[0].averageCost").value("110.00"))
                .andExpect(jsonPath("$[0].cost").value("1650.00"))
                .andExpect(jsonPath("$[0].realizedGain").value("200.00"))
                .andExpect(jsonPath("$[0].closed").value(false))
                .andExpect(jsonPath("$[0].incomplete").value(false));
    }

    @Test
    void fifoAccountUsesFifo() throws Exception {
        String fifo = createAccount("F", ",\"costMethod\":\"fifo\"");
        trade(fifo, "buy", "2026-01-01", "PAS", "10", "100");
        trade(fifo, "buy", "2026-02-01", "PAS", "10", "120");
        trade(fifo, "sell", "2026-03-01", "PAS", "5", "150");

        as(anna, get("/api/v1/positions"))
                .andExpect(jsonPath("$[0].cost").value("1700.00"))
                .andExpect(jsonPath("$[0].realizedGain").value("250.00"));
    }

    @Test
    void changingTheMethodRecalculates() throws Exception {
        trade(account, "buy", "2026-01-01", "PAS", "10", "100");
        trade(account, "buy", "2026-02-01", "PAS", "10", "120");
        trade(account, "sell", "2026-03-01", "PAS", "5", "150");

        as(anna, patch("/api/v1/accounts/" + account).contentType(MediaType.APPLICATION_JSON)
                .content("{\"costMethod\":\"fifo\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.costMethod").value("fifo"));

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].realizedGain").value("250.00"));
    }

    @Test
    void backfilledOrderFollowsDatesNotEntryOrder() throws Exception {
        String fifo = createAccount("F", ",\"costMethod\":\"fifo\"");
        trade(fifo, "sell", "2026-03-01", "PAS", "5", "150");
        trade(fifo, "buy", "2026-02-01", "PAS", "10", "120");
        trade(fifo, "buy", "2026-01-01", "PAS", "10", "100");

        as(anna, get("/api/v1/positions"))
                .andExpect(jsonPath("$[0].realizedGain").value("250.00"))
                .andExpect(jsonPath("$[0].incomplete").value(false));
    }

    @Test
    void holdingsAreSeparatePerAccountAndTicker() throws Exception {
        String other = createAccount("B", "");
        trade(account, "buy", "2026-01-01", "XTB", "1", "10");
        trade(account, "buy", "2026-01-01", "PAS", "1", "20");
        trade(other, "buy", "2026-01-01", "PAS", "1", "30");

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].ticker").value("PAS"))
                .andExpect(jsonPath("$[0].cost").value("20.00"))
                .andExpect(jsonPath("$[1].ticker").value("PAS"))
                .andExpect(jsonPath("$[1].cost").value("30.00"))
                .andExpect(jsonPath("$[2].ticker").value("XTB"));
        as(anna, get("/api/v1/positions").param("account", other)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void closedPositionsAreHiddenUnlessAskedFor() throws Exception {
        trade(account, "buy", "2026-01-01", "PAS", "10", "100");
        trade(account, "sell", "2026-02-01", "PAS", "10", "110");

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$.length()").value(0));
        as(anna, get("/api/v1/positions").param("closed", "true"))
                .andExpect(jsonPath("$[0].closed").value(true))
                .andExpect(jsonPath("$[0].quantity").value("0"))
                .andExpect(jsonPath("$[0].averageCost").doesNotExist())
                .andExpect(jsonPath("$[0].realizedGain").value("100.00"));
    }

    @Test
    void incompletePositionShowsEvenWhenNothingIsHeld() throws Exception {
        trade(account, "buy", "2026-01-01", "PAS", "5", "100");
        trade(account, "sell", "2026-02-01", "PAS", "10", "200");

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].incomplete").value(true))
                .andExpect(jsonPath("$[0].realizedGain").value("500.00"));
    }

    @Test
    void cashTransactionsAndDividendsAreNotPositions() throws Exception {
        String json = """
                {"accountId":"%s","type":"dividend","date":"2026-01-01","ticker":"PAS","amount":"10","affectsBalance":true}"""
                .formatted(account);
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());

        as(anna, get("/api/v1/positions").param("closed", "true")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removingAnInstrumentKeepsThePosition() throws Exception {
        trade(account, "buy", "2026-01-01", "PAS", "1", "10");
        String body = as(anna, get("/api/v1/instruments")).andReturn().getResponse().getContentAsString();

        as(anna, delete("/api/v1/instruments/" + JsonPath.<String>read(body, "$[0].id")))
                .andExpect(status().isNoContent());

        as(anna, get("/api/v1/positions")).andExpect(jsonPath("$[0].ticker").value("PAS"));
    }

    @Test
    void positionsArePrivate() throws Exception {
        trade(account, "buy", "2026-01-01", "PAS", "1", "10");

        as(bob, get("/api/v1/positions")).andExpect(jsonPath("$.length()").value(0));
        as(bob, get("/api/v1/positions").param("account", account)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/v1/positions")).andExpect(status().isUnauthorized());
    }
}
