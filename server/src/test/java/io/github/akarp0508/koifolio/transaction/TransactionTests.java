package io.github.akarp0508.koifolio.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class TransactionTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private String anna;
    private String bob;
    private String account;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
        account = createAccount(anna, "PLN", "1000.00");
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

    private String createAccount(String token, String currency, String balance) throws Exception {
        String json = "{\"name\":\"A\",\"currency\":\"%s\",\"balance\":\"%s\"}".formatted(currency, balance);
        String body = as(token, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions add(String token, String accountId, String fields) throws Exception {
        String json = "{\"accountId\":\"%s\",%s}".formatted(accountId, fields);
        return as(token, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions add(String fields) throws Exception {
        return add(anna, account, fields);
    }

    private String addedId(String fields) throws Exception {
        String body = add(fields).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions replace(String token, String id, String fields) throws Exception {
        return as(token, put("/api/v1/transactions/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{" + fields + "}"));
    }

    private static String deposit(String amount) {
        return "\"type\":\"deposit\",\"date\":\"2026-03-01\",\"amount\":\"%s\",\"affectsBalance\":true".formatted(amount);
    }

    private static String trade(String type, String date, String quantity, String price) {
        return "\"type\":\"%s\",\"date\":\"%s\",\"ticker\":\"PAS\",\"quantity\":\"%s\",\"price\":\"%s\",\"affectsBalance\":true"
                .formatted(type, date, quantity, price);
    }

    private void expectBalance(String expected) throws Exception {
        as(anna, get("/api/v1/accounts/" + account)).andExpect(jsonPath("$.balance").value(expected));
    }

    @Test
    void depositRaisesTheBalance() throws Exception {
        add(deposit("500.50")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("deposit"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.amount").value("500.50"))
                .andExpect(jsonPath("$.cashEffect").value("500.50"))
                .andExpect(jsonPath("$.ticker").doesNotExist());

        expectBalance("1500.50");
    }

    @Test
    void withdrawalAndFeeLowerTheBalance() throws Exception {
        add("\"type\":\"withdrawal\",\"date\":\"2026-03-01\",\"amount\":\"100\",\"affectsBalance\":true")
                .andExpect(jsonPath("$.cashEffect").value("-100.00"));
        add("\"type\":\"fee\",\"date\":\"2026-03-01\",\"amount\":\"10\",\"affectsBalance\":true")
                .andExpect(jsonPath("$.cashEffect").value("-10.00"));

        expectBalance("890.00");
    }

    @Test
    void buyCostsPricePlusFee() throws Exception {
        add("\"type\":\"buy\",\"date\":\"2026-03-01\",\"ticker\":\"pas\",\"quantity\":\"10\",\"price\":\"50\",\"fee\":\"5\",\"affectsBalance\":true")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("PAS"))
                .andExpect(jsonPath("$.quantity").value("10"))
                .andExpect(jsonPath("$.price").value("50"))
                .andExpect(jsonPath("$.fee").value("5.00"))
                .andExpect(jsonPath("$.cashEffect").value("-505.00"));

        expectBalance("495.00");
    }

    @Test
    void sellBringsPriceMinusFee() throws Exception {
        add(trade("buy", "2026-03-01", "10", "50"));
        add("\"type\":\"sell\",\"date\":\"2026-03-02\",\"ticker\":\"PAS\",\"quantity\":\"10\",\"price\":\"60\",\"fee\":\"5\",\"affectsBalance\":true")
                .andExpect(jsonPath("$.cashEffect").value("595.00"));

        expectBalance("1095.00");
    }

    @Test
    void dividendAddsTheNetAmount() throws Exception {
        add("\"type\":\"dividend\",\"date\":\"2026-03-01\",\"ticker\":\"PAS\",\"amount\":\"100\",\"withheldTax\":\"19\",\"affectsBalance\":true")
                .andExpect(jsonPath("$.amount").value("100.00"))
                .andExpect(jsonPath("$.withheldTax").value("19.00"))
                .andExpect(jsonPath("$.cashEffect").value("81.00"));

        expectBalance("1081.00");
    }

    @Test
    void netOnlyDividendHasNoWithheldTax() throws Exception {
        add("\"type\":\"dividend\",\"date\":\"2026-03-01\",\"ticker\":\"PAS\",\"amount\":\"81\",\"affectsBalance\":true")
                .andExpect(jsonPath("$.cashEffect").value("81.00"));
    }

    @Test
    void cashEffectIsRoundedToTheCurrency() throws Exception {
        add(trade("buy", "2026-03-01", "0.333", "10.005"))
                .andExpect(jsonPath("$.cashEffect").value("-3.33"));

        expectBalance("996.67");
    }

    @Test
    void fractionalQuantityAndPreciseUnitPriceAreKept() throws Exception {
        add(trade("buy", "2026-03-01", "0.12345678", "0.123456"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value("0.12345678"))
                .andExpect(jsonPath("$.price").value("0.123456"));
    }

    @Test
    void backfilledTransactionLeavesTheBalanceAlone() throws Exception {
        add("\"type\":\"buy\",\"date\":\"2024-03-12\",\"ticker\":\"PAS\",\"quantity\":\"10\",\"price\":\"50\",\"affectsBalance\":false")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.affectsBalance").value(false))
                .andExpect(jsonPath("$.cashEffect").value("-500.00"));

        expectBalance("1000.00");
    }

    @Test
    void affectsBalanceMustBeChosen() throws Exception {
        add("\"type\":\"deposit\",\"date\":\"2026-03-01\",\"amount\":\"1\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("affectsBalance"));
    }

    @Test
    void editMovesTheBalanceByTheDifference() throws Exception {
        String id = addedId(deposit("500"));

        replace(anna, id, deposit("200")).andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("200.00"));

        expectBalance("1200.00");
    }

    @Test
    void editCanChangeTheType() throws Exception {
        String id = addedId(deposit("500"));

        replace(anna, id, "\"type\":\"withdrawal\",\"date\":\"2026-03-01\",\"amount\":\"500\",\"affectsBalance\":true");

        expectBalance("500.00");
    }

    @Test
    void turningAffectsBalanceOffUndoesItsEffect() throws Exception {
        String id = addedId(deposit("500"));

        replace(anna, id, "\"type\":\"deposit\",\"date\":\"2026-03-01\",\"amount\":\"500\",\"affectsBalance\":false");

        expectBalance("1000.00");
    }

    @Test
    void editDoesNotMoveATransactionToAnotherAccount() throws Exception {
        String other = createAccount(anna, "PLN", "0.00");
        String id = addedId(deposit("500"));

        replace(anna, id, "\"accountId\":\"%s\",%s".formatted(other, deposit("500")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account));
    }

    @Test
    void invalidEditChangesNothing() throws Exception {
        String id = addedId(deposit("500"));

        replace(anna, id, "\"type\":\"deposit\",\"date\":\"2026-03-01\",\"amount\":\"-1\",\"affectsBalance\":true")
                .andExpect(status().isBadRequest());

        expectBalance("1500.00");
        as(anna, get("/api/v1/transactions/" + id)).andExpect(jsonPath("$.amount").value("500.00"));
    }

    @Test
    void deleteUndoesItsEffect() throws Exception {
        String id = addedId(deposit("500"));

        as(anna, delete("/api/v1/transactions/" + id)).andExpect(status().isNoContent());

        expectBalance("1000.00");
        as(anna, get("/api/v1/transactions/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("transaction_not_found"));
    }

    @Test
    void deletingABackfilledTransactionLeavesTheBalanceAlone() throws Exception {
        String id = addedId("\"type\":\"deposit\",\"date\":\"2026-03-01\",\"amount\":\"500\",\"affectsBalance\":false");

        as(anna, delete("/api/v1/transactions/" + id)).andExpect(status().isNoContent());

        expectBalance("1000.00");
    }

    @Test
    void settingTheBalanceByHandKeepsTransactions() throws Exception {
        addedId(deposit("500"));

        as(anna, patch("/api/v1/accounts/" + account).contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\":\"42.00\"}")).andExpect(status().isOk());

        expectBalance("42.00");
        as(anna, get("/api/v1/transactions")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void balanceSetOnOnlyMovesWhenTheBalanceIsChangedByHand() throws Exception {
        jdbc.sql("UPDATE accounts SET balance_set_on = '2020-01-01'").update();

        addedId(deposit("500"));
        as(anna, get("/api/v1/accounts/" + account)).andExpect(jsonPath("$.balanceSetOn").value("2020-01-01"));

        setBalance("1500.00");
        as(anna, get("/api/v1/accounts/" + account)).andExpect(jsonPath("$.balanceSetOn").value("2020-01-01"));

        setBalance("42.00");
        as(anna, get("/api/v1/accounts/" + account))
                .andExpect(jsonPath("$.balanceSetOn").value(LocalDate.now().toString()));
    }

    private void setBalance(String balance) throws Exception {
        as(anna, patch("/api/v1/accounts/" + account).contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\":\"%s\"}".formatted(balance))).andExpect(status().isOk());
    }

    @Test
    void listsNewestFirstAndFiltersByAccount() throws Exception {
        String other = createAccount(anna, "PLN", "0.00");
        add(deposit("1"));
        add("\"type\":\"deposit\",\"date\":\"2026-05-01\",\"amount\":\"2\",\"affectsBalance\":true");
        add(anna, other, deposit("3"));

        as(anna, get("/api/v1/transactions")).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].amount").value("2.00"));
        as(anna, get("/api/v1/transactions").param("account", other))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value("3.00"));
    }

    @Test
    void currencyFollowsTheAccount() throws Exception {
        String yen = createAccount(anna, "JPY", "0");

        add(anna, yen, deposit("1000")).andExpect(jsonPath("$.currency").value("JPY"))
                .andExpect(jsonPath("$.cashEffect").value("1000"));
        add(anna, yen, deposit("10.5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void oversoldSaleIsSavedAndMarked() throws Exception {
        add(trade("buy", "2026-03-01", "5", "10"));

        add(trade("sell", "2026-03-02", "10", "10")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.oversold").value(true));

        expectBalance("1050.00");
    }

    @Test
    void marksClearOnceTheHistoryAddsUp() throws Exception {
        String sale = addedId(trade("sell", "2026-03-02", "10", "10"));
        as(anna, get("/api/v1/transactions/" + sale)).andExpect(jsonPath("$.oversold").value(true));

        add(trade("buy", "2026-03-01", "10", "10")).andExpect(jsonPath("$.oversold").value(false));

        as(anna, get("/api/v1/transactions/" + sale)).andExpect(jsonPath("$.oversold").value(false));
    }

    @Test
    void buyAfterTheSaleDoesNotCoverIt() throws Exception {
        String sale = addedId(trade("sell", "2026-03-02", "10", "10"));

        add(trade("buy", "2026-03-03", "10", "10"));

        as(anna, get("/api/v1/transactions/" + sale)).andExpect(jsonPath("$.oversold").value(true));
    }

    @Test
    void oversoldIsCountedPerAccountAndTicker() throws Exception {
        String other = createAccount(anna, "PLN", "0.00");
        add(anna, other, trade("buy", "2026-03-01", "10", "10") + ",\"allowNegativeBalance\":true");
        add("\"type\":\"buy\",\"date\":\"2026-03-01\",\"ticker\":\"XYZ\",\"quantity\":\"10\",\"price\":\"1\",\"affectsBalance\":true");

        add(trade("sell", "2026-03-02", "1", "10")).andExpect(jsonPath("$.oversold").value(true));
    }

    @Test
    void missingFieldsAreNamedForTheType() throws Exception {
        add("\"type\":\"buy\",\"date\":\"2026-03-01\",\"affectsBalance\":true")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("price"))
                .andExpect(jsonPath("$.errors[1].field").value("quantity"))
                .andExpect(jsonPath("$.errors[2].field").value("ticker"));
    }

    @Test
    void amountsMustBePositive() throws Exception {
        add(deposit("0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("amount must be greater than 0"));
        add(trade("buy", "2026-03-01", "-1", "10")).andExpect(status().isBadRequest());
        add(trade("buy", "2026-03-01", "1", "-10")).andExpect(status().isBadRequest());
    }

    @Test
    void freeSharesHaveAZeroPrice() throws Exception {
        add(trade("buy", "2026-03-01", "1", "0")).andExpect(status().isCreated());
    }

    @Test
    void withheldTaxCannotExceedTheDividend() throws Exception {
        add("\"type\":\"dividend\",\"date\":\"2026-03-01\",\"ticker\":\"PAS\",\"amount\":\"10\",\"withheldTax\":\"11\",\"affectsBalance\":true")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("withheldTax"));
    }

    @Test
    void unknownTypeNamesTheField() throws Exception {
        add("\"type\":\"gift\",\"date\":\"2026-03-01\",\"amount\":\"1\",\"affectsBalance\":true")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("type has an invalid value"));
    }

    @Test
    void accountIsRequiredWhenCreating() throws Exception {
        as(anna, post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content("{" + deposit("1") + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("accountId"));
    }

    @Test
    void balanceOutOfRangeIsRejectedNotCrashed() throws Exception {
        add(deposit("999999999999999.00")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("balance_out_of_range"));

        expectBalance("1000.00");
    }

    @Test
    void transactionTooLargeForTheColumnIsRejectedEvenWithoutBalanceEffect() throws Exception {
        add("\"type\":\"buy\",\"date\":\"2026-03-01\",\"ticker\":\"PAS\",\"quantity\":\"999999999999999\",\"price\":\"9999999999999\",\"affectsBalance\":false")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("amount_out_of_range"));
    }

    @Test
    void otherUsersCannotSeeOrTouchATransaction() throws Exception {
        String id = addedId(deposit("500"));

        as(bob, get("/api/v1/transactions")).andExpect(jsonPath("$.length()").value(0));
        as(bob, get("/api/v1/transactions").param("account", account)).andExpect(jsonPath("$.length()").value(0));
        as(bob, get("/api/v1/transactions/" + id)).andExpect(status().isNotFound());
        replace(bob, id, deposit("1")).andExpect(status().isNotFound());
        as(bob, delete("/api/v1/transactions/" + id)).andExpect(status().isNotFound());

        expectBalance("1500.00");
    }

    @Test
    void cannotAddToAnotherUsersAccount() throws Exception {
        add(bob, account, deposit("500")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("account_not_found"));

        expectBalance("1000.00");
    }

    @Test
    void deletingAnAccountDeletesItsTransactions() throws Exception {
        addedId(deposit("500"));

        as(anna, delete("/api/v1/accounts/" + account)).andExpect(status().isNoContent());

        as(anna, get("/api/v1/transactions")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void goingNegativeAsksFirstAndChangesNothing() throws Exception {
        add(trade("buy", "2026-03-01", "20", "60")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("negative_balance"))
                .andExpect(jsonPath("$.detail").value("This would take A to -200.00 PLN"));

        expectBalance("1000.00");
        as(anna, get("/api/v1/transactions")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void goingNegativeWorksOnceConfirmed() throws Exception {
        add(trade("buy", "2026-03-01", "20", "60") + ",\"allowNegativeBalance\":true")
                .andExpect(status().isCreated());

        expectBalance("-200.00");
    }

    @Test
    void reachingExactlyZeroDoesNotAsk() throws Exception {
        add(trade("buy", "2026-03-01", "10", "100")).andExpect(status().isCreated());

        expectBalance("0.00");
    }

    @Test
    void withdrawalsAndFeesAskToo() throws Exception {
        add("\"type\":\"withdrawal\",\"date\":\"2026-03-01\",\"amount\":\"1001\",\"affectsBalance\":true")
                .andExpect(status().isConflict());
        add("\"type\":\"fee\",\"date\":\"2026-03-01\",\"amount\":\"1001\",\"affectsBalance\":true")
                .andExpect(status().isConflict());
    }

    @Test
    void backfillNeverAsks() throws Exception {
        add("\"type\":\"buy\",\"date\":\"2024-03-01\",\"ticker\":\"PAS\",\"quantity\":\"100\",\"price\":\"100\",\"affectsBalance\":false")
                .andExpect(status().isCreated());
    }

    @Test
    void editThatGoesNegativeAsksAndChangesNothing() throws Exception {
        String id = addedId(trade("buy", "2026-03-01", "1", "100"));

        replace(anna, id, trade("buy", "2026-03-01", "20", "60")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("negative_balance"));

        expectBalance("900.00");
        as(anna, get("/api/v1/transactions/" + id)).andExpect(jsonPath("$.quantity").value("1"));
    }

    @Test
    void anAlreadyNegativeAccountDoesNotAskForChangesThatDoNotLowerIt() throws Exception {
        setBalance("-50.00");

        add(deposit("10")).andExpect(status().isCreated());
        String id = addedId(deposit("10"));
        replace(anna, id, deposit("10")).andExpect(status().isOk());

        add(trade("buy", "2026-03-01", "1", "1")).andExpect(status().isConflict());
    }

    @Test
    void newTickerJoinsTheInstrumentList() throws Exception {
        add(trade("buy", "2026-03-01", "1", "1"));
        add("\"type\":\"dividend\",\"date\":\"2026-03-01\",\"ticker\":\"xtb\",\"amount\":\"1\",\"affectsBalance\":true");
        add(trade("buy", "2026-03-02", "1", "1"));

        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticker").value("PAS"))
                .andExpect(jsonPath("$[1].ticker").value("XTB"));
        as(bob, get("/api/v1/instruments")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void editingToANewTickerAddsIt() throws Exception {
        String id = addedId(trade("buy", "2026-03-01", "1", "1"));

        replace(anna, id, trade("buy", "2026-03-01", "1", "1").replace("PAS", "NEW"));

        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void cancelledTransactionDoesNotAddItsTicker() throws Exception {
        add(trade("buy", "2026-03-01", "20", "60")).andExpect(status().isConflict());

        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cashTransactionsAddNothing() throws Exception {
        add(deposit("1"));

        as(anna, get("/api/v1/instruments")).andExpect(jsonPath("$.length()").value(0));
    }

    private static String split(String date, String from, String to) {
        return "\"type\":\"split\",\"date\":\"%s\",\"ticker\":\"PAS\",\"splitFrom\":\"%s\",\"splitTo\":\"%s\",\"affectsBalance\":true"
                .formatted(date, from, to);
    }

    @Test
    void aSplitNeverTouchesTheBalance() throws Exception {
        add(trade("buy", "2026-03-01", "10", "50"));

        add(split("2026-03-02", "1", "10")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("split"))
                .andExpect(jsonPath("$.ticker").value("PAS"))
                .andExpect(jsonPath("$.splitFrom").value("1"))
                .andExpect(jsonPath("$.splitTo").value("10"))
                .andExpect(jsonPath("$.cashEffect").value("0.00"))
                .andExpect(jsonPath("$.affectsBalance").value(false));

        expectBalance("500.00");
    }

    @Test
    void sellingTheSplitSharesIsNotOversold() throws Exception {
        add(trade("buy", "2026-03-01", "10", "50"));
        add(split("2026-03-02", "1", "10"));

        add(trade("sell", "2026-03-03", "100", "5")).andExpect(jsonPath("$.oversold").value(false));
    }

    @Test
    void aSplitBackfilledLaterClearsTheWarning() throws Exception {
        add(trade("buy", "2026-03-01", "10", "50"));
        String sale = addedId(trade("sell", "2026-03-03", "100", "5"));
        as(anna, get("/api/v1/transactions/" + sale)).andExpect(jsonPath("$.oversold").value(true));

        add(split("2026-03-02", "1", "10"));

        as(anna, get("/api/v1/transactions/" + sale)).andExpect(jsonPath("$.oversold").value(false));
    }

    @Test
    void aSplitNeedsBothSides() throws Exception {
        add("\"type\":\"split\",\"date\":\"2026-03-02\",\"ticker\":\"PAS\",\"affectsBalance\":false")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        add(split("2026-03-02", "0", "10")).andExpect(status().isBadRequest());
        add(split("2026-03-02", "2", "2")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("splitTo"));
    }

    @Test
    void aSplitCanBeEditedAndDeleted() throws Exception {
        String id = addedId(split("2026-03-02", "1", "10"));

        replace(anna, id, split("2026-03-02", "1", "5")).andExpect(jsonPath("$.splitTo").value("5"));
        as(anna, delete("/api/v1/transactions/" + id)).andExpect(status().isNoContent());
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/v1/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentTransactionsDoNotLoseBalanceUpdates() throws Exception {
        int count = 20;
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                calls.add(() -> add(deposit("1")).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : pool.invokeAll(calls)) {
                assertThat(result.get()).isEqualTo(201);
            }
        }

        expectBalance("1020.00");
    }

    @Test
    void unknownIdIsNotFound() throws Exception {
        as(anna, get("/api/v1/transactions/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
