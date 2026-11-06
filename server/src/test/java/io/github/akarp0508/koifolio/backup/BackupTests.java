package io.github.akarp0508.koifolio.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import io.github.akarp0508.koifolio.auth.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabase.class)
class BackupTests {

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
        jdbc.sql("DELETE FROM fx_rates").update();
        jdbc.sql("INSERT INTO fx_rates VALUES ('USD', ?, current_date, now())").param(new BigDecimal("4")).update();
        anna = tokenFor("anna");
        bob = tokenFor("bob");
        ike = createAccount(anna, "IKE (XTB)", "PLN", "1000.50", "{\"costMethod\":\"fifo\",\"taxRate\":\"19\"}");
        usd = createAccount(anna, "IKE-XTB", "USD", "0", "{}");
        fill();
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

    private ResultActions send(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
        return as(token, request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createAccount(String token, String name, String currency, String balance, String extra) throws Exception {
        String json = extra.replaceFirst("\\{", "{\"name\":\"%s\",\"currency\":\"%s\",\"balance\":\"%s\"%s"
                .formatted(name, currency, balance, extra.equals("{}") ? "" : ","));
        String body = send(token, post("/api/v1/accounts"), json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void transaction(String json) throws Exception {
        send(anna, post("/api/v1/transactions"), json).andExpect(status().isCreated());
    }

    /** One of everything an export has to carry. */
    private void fill() throws Exception {
        String account = "\"accountId\":\"%s\",\"date\":\"2026-02-01\",\"affectsBalance\":%s,";
        String pln = account.formatted(ike, "true");
        transaction("{" + pln + "\"type\":\"deposit\",\"amount\":\"5000\"}");
        transaction("{" + pln + "\"note\":\"After the results\",\"type\":\"buy\",\"ticker\":\"PAS\",\"quantity\":\"10.5\",\"price\":\"120.123456\",\"fee\":\"3.15\"}");
        transaction("{" + pln + "\"type\":\"sell\",\"ticker\":\"PAS\",\"quantity\":\"2\",\"price\":\"130\"}");
        transaction("{" + pln + "\"type\":\"dividend\",\"ticker\":\"PAS\",\"amount\":\"25\",\"withheldTax\":\"4.75\"}");
        transaction("{" + pln + "\"type\":\"fee\",\"amount\":\"1\"}");
        transaction("{" + pln + "\"type\":\"withdrawal\",\"amount\":\"10\"}");
        transaction("{\"accountId\":\"%s\",\"date\":\"2026-03-01\",\"affectsBalance\":false,\"type\":\"split\",\"ticker\":\"PAS\",\"splitFrom\":\"1\",\"splitTo\":\"10\"}".formatted(ike));
        transaction("{\"accountId\":\"%s\",\"date\":\"2026-03-02\",\"affectsBalance\":false,\"type\":\"spinoff\",\"ticker\":\"NEWCO\",\"parentTicker\":\"PAS\",\"quantity\":\"3\",\"costShare\":\"12.5\"}".formatted(ike));
        transaction("{" + account.formatted(usd, "false") + "\"type\":\"buy\",\"ticker\":\"GONE\",\"quantity\":\"1\",\"price\":\"5\"}");

        String pas = jdbc.sql("SELECT id::text FROM instruments WHERE ticker = 'PAS' AND user_id = (SELECT id FROM users WHERE username = 'anna')")
                .query(String.class).single();
        send(anna, put("/api/v1/instruments/" + pas), """
                {"name":"Passus: \\"S.A.\\" ąę","sector":"IT","risk":3,"thesis":"line one\\nline two",\
                "analysis":"# Heading\\n\\n---\\n\\nbody\\n","tags":["bank","core"]}""").andExpect(status().isOk());
        send(anna, post("/api/v1/instruments"), "{\"ticker\":\"WATCH\",\"sector\":\"Energy\"}").andExpect(status().isCreated());
        send(anna, put("/api/v1/positions/%s/PAS/plan".formatted(ike)), """
                {"plan":"hold","risk":2,"targetPrice":"180.5","stopPrice":"90","tags":["long"]}""").andExpect(status().isOk());
        String gone = jdbc.sql("SELECT id::text FROM instruments WHERE ticker = 'GONE'").query(String.class).single();
        as(anna, delete("/api/v1/instruments/" + gone)).andExpect(status().isNoContent());
        send(anna, post("/api/v1/rules"), "{\"type\":\"max_position_weight\",\"limitPercent\":\"20\"}").andExpect(status().isCreated());
        send(anna, post("/api/v1/rules"), """
                {"type":"min_low_risk_weight","limitPercent":"50.5","maxRisk":2,"accountId":"%s"}""".formatted(ike))
                .andExpect(status().isCreated());
        send(anna, patch("/api/v1/settings"), "{\"baseCurrency\":\"USD\",\"costFxRule\":\"transaction_date\"}")
                .andExpect(status().isOk());
    }

    private byte[] export(String token) throws Exception {
        return as(token, get("/api/v1/export")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private ResultActions restore(String token, byte[] zip) throws Exception {
        return upload(token, zip, "replace");
    }

    private ResultActions upload(String token, byte[] zip, String mode) throws Exception {
        return mvc.perform(multipart("/api/v1/import").file(new MockMultipartFile("file", "export.zip", "application/zip", zip))
                .param("mode", mode).header("Authorization", "Bearer " + token));
    }

    private static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                out.putNextEntry(new ZipEntry(file.getKey()));
                out.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** The time of the export is the one thing that should differ. */
    private static Map<String, String> withoutExportTime(Map<String, String> files) {
        Map<String, String> copy = new LinkedHashMap<>(files);
        copy.put("koifolio.yaml", copy.get("koifolio.yaml").replaceAll("exportedAt: .*", ""));
        return copy;
    }

    @Test
    void exportsReadableFiles() throws Exception {
        Map<String, String> files = unzip(export(anna));

        assertThat(files).containsKeys("koifolio.yaml", "accounts.yaml", "plans.yaml", "rules.yaml",
                "transactions/ike-xtb.jsonl", "transactions/ike-xtb-2.jsonl", "companies/PAS.md", "companies/WATCH.md");
        assertThat(files.get("transactions/ike-xtb.jsonl")).contains("\"quantity\":10.5").contains("\"price\":120.123456");
        assertThat(files.get("companies/PAS.md")).startsWith("---\n").contains("# Heading");
        assertThat(String.join("", files.values())).doesNotContain("password");
    }

    @Test
    void anExportRestoresExactly() throws Exception {
        byte[] exported = export(anna);

        restore(bob, exported).andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").value(2))
                .andExpect(jsonPath("$.transactions").value(9))
                .andExpect(jsonPath("$.companies").value(4))
                .andExpect(jsonPath("$.plans").value(1))
                .andExpect(jsonPath("$.rules").value(2));

        assertThat(withoutExportTime(unzip(export(bob)))).isEqualTo(withoutExportTime(unzip(exported)));
    }

    @Test
    void restoringKeepsBalancesPositionsAndOrder() throws Exception {
        String accounts = as(anna, get("/api/v1/accounts")).andReturn().getResponse().getContentAsString();
        String positions = as(anna, get("/api/v1/positions")).andReturn().getResponse().getContentAsString();

        restore(bob, export(anna)).andExpect(status().isOk());

        as(bob, get("/api/v1/accounts"))
                .andExpect(jsonPath("$[0].balance").value((String) JsonPath.read(accounts, "$[0].balance")))
                .andExpect(jsonPath("$[0].taxRate").value("19"));
        as(bob, get("/api/v1/positions"))
                .andExpect(jsonPath("$[1].quantity").value((String) JsonPath.read(positions, "$[1].quantity")))
                .andExpect(jsonPath("$[1].cost").value((String) JsonPath.read(positions, "$[1].cost")));
        as(bob, get("/api/v1/instruments")).andExpect(jsonPath("$[?(@.ticker == 'GONE')]").isEmpty());
        as(bob, get("/api/v1/settings")).andExpect(jsonPath("$.baseCurrency").value("USD"));
    }

    @Test
    void restoringReplacesWhatWasThere() throws Exception {
        createAccount(bob, "Old", "PLN", "5", "{}");

        restore(bob, export(anna)).andExpect(status().isOk());

        as(bob, get("/api/v1/accounts")).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name == 'Old')]").isEmpty());
    }

    @Test
    void restoringYourOwnExportChangesNothing() throws Exception {
        byte[] exported = export(anna);

        restore(anna, exported).andExpect(status().isOk());

        assertThat(withoutExportTime(unzip(export(anna)))).isEqualTo(withoutExportTime(unzip(exported)));
    }

    @Test
    void aBadLineNamesItselfAndChangesNothing() throws Exception {
        Map<String, String> files = unzip(export(anna));
        String lines = files.get("transactions/ike-xtb.jsonl");
        files.put("transactions/ike-xtb.jsonl", lines.replaceFirst("\"quantity\":10.5", "\"quantity\":-10.5"));
        byte[] before = export(bob);

        restore(bob, zip(files)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_import"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("transactions/ike-xtb.jsonl line 2")));

        assertThat(withoutExportTime(unzip(export(bob)))).isEqualTo(withoutExportTime(unzip(before)));
    }

    @Test
    void aLineThatIsntJsonIsNamed() throws Exception {
        Map<String, String> files = unzip(export(anna));
        files.put("transactions/ike-xtb.jsonl", "not json\n");

        restore(bob, zip(files)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("line 1")));
    }

    @Test
    void aMissingAccountKeyIsRefusedBeforeAnythingIsDeleted() throws Exception {
        createAccount(bob, "Old", "PLN", "5", "{}");
        Map<String, String> files = unzip(export(anna));
        files.put("transactions/nobody.jsonl", "");

        restore(bob, zip(files)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no account has this key")));

        as(bob, get("/api/v1/accounts")).andExpect(jsonPath("$[0].name").value("Old"));
    }

    @Test
    void handWrittenLinesKeepTheirOrder() throws Exception {
        Map<String, String> files = unzip(export(anna));
        files.keySet().removeIf(name -> name.startsWith("companies/") || name.startsWith("transactions/"));
        files.put("plans.yaml", "[]\n");
        files.put("rules.yaml", "[]\n");
        files.put("transactions/ike-xtb.jsonl", """
                {"date":"2026-01-01","type":"buy","ticker":"ABC","quantity":1,"price":10,"affectsBalance":false}
                {"date":"2026-01-01","type":"sell","ticker":"ABC","quantity":1,"price":20,"affectsBalance":false}
                """);

        restore(bob, zip(files)).andExpect(status().isOk());

        as(bob, get("/api/v1/positions?closed=true"))
                .andExpect(jsonPath("$[0].incomplete").value(false))
                .andExpect(jsonPath("$[0].realizedGain").value("10.00"));
    }

    @Test
    void anotherFormatIsRefused() throws Exception {
        Map<String, String> files = unzip(export(anna));
        files.put("koifolio.yaml", files.get("koifolio.yaml").replace("format: 1", "format: 2"));

        restore(bob, zip(files)).andExpect(status().isBadRequest());
    }

    @Test
    void aFileThatIsntAnExportIsRefused() throws Exception {
        restore(bob, "hello".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest());
        restore(bob, zip(Map.of("other.txt", "hello"))).andExpect(status().isBadRequest());
    }

    @Test
    void importNeedsTheReplaceMode() throws Exception {
        upload(bob, export(anna), "merge").andExpect(status().isBadRequest());
    }

    @Test
    void exportOnlyHoldsYourOwnData() throws Exception {
        assertThat(String.join("", unzip(export(bob)).values())).doesNotContain("PAS");
    }

    @Test
    void needsAToken() throws Exception {
        mvc.perform(get("/api/v1/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void aRestoreDropsChangesWaitingForReview() throws Exception {
        jdbc.sql("""
                INSERT INTO pending_changes (id, user_id, agent_name, kind, target_id, changes, summary, created_at)
                SELECT gen_random_uuid(), id, 'Claude', 'RULE_DELETE', gen_random_uuid(), '{}', 'Remove rule', now()
                FROM users WHERE username = 'anna'""").update();

        restore(anna, export(anna)).andExpect(status().isOk());

        as(anna, get("/api/v1/review/changes")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void marksSurviveARestoreAndDeletedTransactionsDont() throws Exception {
        var transactions = jdbc.sql("SELECT id FROM transactions WHERE user_id = (SELECT id FROM users WHERE username = 'anna') ORDER BY created_at")
                .query(java.util.UUID.class).list();
        jdbc.sql("UPDATE transactions SET created_by = 'Claude', validated_at = NULL WHERE id = ?").param(transactions.get(0)).update();
        jdbc.sql("UPDATE transactions SET deleted_at = now() WHERE id = ?").param(transactions.get(1)).update();

        byte[] zip = export(anna);
        restore(bob, zip).andExpect(status().isOk());

        String restored = as(bob, get("/api/v1/transactions")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(restored, "$[*]")).hasSize(transactions.size() - 1);
        assertThat(JsonPath.<List<String>>read(restored, "$[?(@.unvalidated == true)].createdBy")).containsExactly("Claude");
        assertThat(JsonPath.<List<String>>read(restored, "$[?(@.unvalidated == false)].createdBy")).isEmpty();
    }
}
