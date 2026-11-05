package io.github.akarp0508.koifolio.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import java.util.UUID;
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
class AccountTests {

    private static final String BROKERAGE =
            "{\"name\":\"Brokerage\",\"description\":\"Main\",\"currency\":\"PLN\",\"balance\":\"5000.00\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    private String anna;
    private String bob;

    @BeforeEach
    void createUsers() throws Exception {
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

    private ResultActions create(String token, String json) throws Exception {
        return as(token, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createdId() throws Exception {
        String body = create(anna, BROKERAGE).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions update(String token, String id, String json) throws Exception {
        return as(token, patch("/api/v1/accounts/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createsAndLists() throws Exception {
        create(anna, BROKERAGE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Brokerage"))
                .andExpect(jsonPath("$.description").value("Main"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.balance").value("5000.00"));

        as(anna, get("/api/v1/accounts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].balance").value("5000.00"));
    }

    @Test
    void newUserHasNoAccounts() throws Exception {
        as(anna, get("/api/v1/accounts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void balanceAndDescriptionAreOptional() throws Exception {
        create(anna, "{\"name\":\"Empty\",\"currency\":\"USD\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value("0.00"))
                .andExpect(jsonPath("$.description").value(""));
    }

    @Test
    void balanceKeepsItsExactValue() throws Exception {
        create(anna, "{\"name\":\"A\",\"currency\":\"PLN\",\"balance\":\"1234567890123.45\"}")
                .andExpect(jsonPath("$.balance").value("1234567890123.45"));
    }

    @Test
    void balanceCanBeSentAsANumber() throws Exception {
        create(anna, "{\"name\":\"A\",\"currency\":\"PLN\",\"balance\":0.1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value("0.10"));
    }

    @Test
    void balanceCanBeNegative() throws Exception {
        create(anna, "{\"name\":\"A\",\"currency\":\"PLN\",\"balance\":\"-10.50\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value("-10.50"));
    }

    @Test
    void decimalPlacesFollowTheCurrency() throws Exception {
        create(anna, "{\"name\":\"A\",\"currency\":\"JPY\",\"balance\":\"1000\"}")
                .andExpect(jsonPath("$.balance").value("1000"));
        create(anna, "{\"name\":\"A\",\"currency\":\"JPY\",\"balance\":\"10.5\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_amount"));
        create(anna, "{\"name\":\"A\",\"currency\":\"PLN\",\"balance\":\"1.234\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("PLN amounts can have at most 2 decimal places"));
    }

    @Test
    void tooLargeBalanceIsRejectedNotCrashed() throws Exception {
        create(anna, "{\"name\":\"A\",\"currency\":\"PLN\",\"balance\":\"10000000000000000\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("balance"));
    }

    @Test
    void nonNumericBalanceNamesTheField() throws Exception {
        create(anna, "{\"name\":\"A\",\"currency\":\"PLN\",\"balance\":\"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value("balance has an invalid value"));
    }

    @Test
    void currencyMustBeReal() throws Exception {
        create(anna, "{\"name\":\"A\",\"currency\":\"ABC\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unknown_currency"));
        create(anna, "{\"name\":\"A\",\"currency\":\"XXX\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unknown_currency"));
        create(anna, "{\"name\":\"A\",\"currency\":\"pln\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void nameAndCurrencyAreRequired() throws Exception {
        create(anna, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void updatesOnlyTheFieldsSent() throws Exception {
        String id = createdId();

        update(anna, id, "{\"balance\":\"7500.50\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("7500.50"))
                .andExpect(jsonPath("$.name").value("Brokerage"))
                .andExpect(jsonPath("$.description").value("Main"));

        as(anna, get("/api/v1/accounts/" + id))
                .andExpect(jsonPath("$.balance").value("7500.50"));
    }

    @Test
    void descriptionCanBeCleared() throws Exception {
        update(anna, createdId(), "{\"description\":\"\"}")
                .andExpect(jsonPath("$.description").value(""));
    }

    @Test
    void nameCannotBeBlanked() throws Exception {
        update(anna, createdId(), "{\"name\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("name must not be blank"));
    }

    @Test
    void updateChecksDecimalPlaces() throws Exception {
        update(anna, createdId(), "{\"balance\":\"1.234\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_amount"));
    }

    @Test
    void currencyCannotBeChanged() throws Exception {
        update(anna, createdId(), "{\"currency\":\"USD\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PLN"));
    }

    @Test
    void deletes() throws Exception {
        String id = createdId();

        as(anna, delete("/api/v1/accounts/" + id)).andExpect(status().isNoContent());

        as(anna, get("/api/v1/accounts/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("account_not_found"));
    }

    @Test
    void otherUsersCannotSeeOrTouchAnAccount() throws Exception {
        String id = createdId();

        as(bob, get("/api/v1/accounts")).andExpect(jsonPath("$.length()").value(0));
        as(bob, get("/api/v1/accounts/" + id)).andExpect(status().isNotFound());
        update(bob, id, "{\"balance\":\"1.00\"}").andExpect(status().isNotFound());
        as(bob, delete("/api/v1/accounts/" + id)).andExpect(status().isNotFound());

        as(anna, get("/api/v1/accounts/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("5000.00"));
    }

    @Test
    void unknownIdIsNotFound() throws Exception {
        as(anna, get("/api/v1/accounts/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfADeletedUserCannotCreateAccounts() throws Exception {
        users.deleteAll();

        create(anna, BROKERAGE).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unknown_user"));
    }

    @Test
    void deletingAUserDeletesTheirAccounts() throws Exception {
        createdId();

        users.deleteAll();

        anna = tokenFor("anna");
        as(anna, get("/api/v1/accounts")).andExpect(jsonPath("$.length()").value(0));
    }
}
