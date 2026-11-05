package io.github.akarp0508.koifolio.instrument;

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
class InstrumentTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    private String anna;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
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

    private ResultActions add(String token, String json) throws Exception {
        return as(token, post("/api/v1/instruments").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String addedId() throws Exception {
        String body = add(anna, "{\"ticker\":\"PAS\",\"name\":\"Passus\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void addsAndListsByTicker() throws Exception {
        add(anna, "{\"ticker\":\" xtb \"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("XTB"))
                .andExpect(jsonPath("$.name").value(""));
        add(anna, "{\"ticker\":\"PAS\",\"name\":\"Passus\"}");

        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticker").value("PAS"))
                .andExpect(jsonPath("$[0].name").value("Passus"))
                .andExpect(jsonPath("$[1].ticker").value("XTB"));
    }

    @Test
    void aTickerIsListedOnce() throws Exception {
        addedId();

        add(anna, "{\"ticker\":\"pas\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("instrument_exists"));
    }

    @Test
    void usersHaveSeparateLists() throws Exception {
        String id = addedId();

        add(bob, "{\"ticker\":\"PAS\"}").andExpect(status().isCreated());
        as(bob, patch("/api/v1/instruments/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
        as(bob, delete("/api/v1/instruments/" + id)).andExpect(status().isNotFound());

        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$[0].name").value("Passus"));
    }

    @Test
    void renames() throws Exception {
        String id = addedId();

        as(anna, patch("/api/v1/instruments/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Passus SA\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Passus SA"))
                .andExpect(jsonPath("$.ticker").value("PAS"));
    }

    @Test
    void removingKeepsTransactionsAndUsingItAgainReAddsIt() throws Exception {
        String id = addedId();
        String body = as(anna, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"currency\":\"PLN\",\"balance\":\"100\"}"))
                .andReturn().getResponse().getContentAsString();
        String trade = "{\"accountId\":\"%s\",\"type\":\"buy\",\"date\":\"2026-03-01\",\"ticker\":\"PAS\",\"quantity\":\"1\",\"price\":\"1\",\"affectsBalance\":true}"
                .formatted(JsonPath.<String>read(body, "$.id"));
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(trade));

        as(anna, delete("/api/v1/instruments/" + id)).andExpect(status().isNoContent());

        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$.length()").value(0));
        as(anna, get("/api/v1/transactions")).andExpect(jsonPath("$[0].ticker").value("PAS"));

        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(trade));
        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void tickerMustBeValid() throws Exception {
        add(anna, "{\"ticker\":\"not valid!\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ticker"));
        add(anna, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/v1/instruments")).andExpect(status().isUnauthorized());
    }
}
