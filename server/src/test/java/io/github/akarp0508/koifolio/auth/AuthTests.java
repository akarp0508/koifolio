package io.github.akarp0508.koifolio.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.akarp0508.koifolio.TestDatabase;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabase.class)
class AuthTests {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    private ResultActions postJson(String path, String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/" + path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private String loginToken() throws Exception {
        postJson("register", "anna", PASSWORD).andExpect(status().isCreated());
        String body = postJson("login", "anna", PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token));
    }

    @Test
    void firstUserRegistersLogsInAndSeesThemselves() throws Exception {
        me(loginToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("anna"));
    }

    @Test
    void passwordIsNotStoredInPlainText() throws Exception {
        postJson("register", "anna", PASSWORD).andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(users.findByUsername("anna").orElseThrow().getPasswordHash())
                .isNotEqualTo(PASSWORD)
                .startsWith("$2");
    }

    @Test
    void registrationClosesAfterTheFirstUser() throws Exception {
        postJson("register", "anna", PASSWORD).andExpect(status().isCreated());

        postJson("register", "bob", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("registration_closed"));
    }

    @Test
    void usernameIgnoresCaseAndSpaces() throws Exception {
        postJson("register", " Anna ", PASSWORD).andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("anna"));

        postJson("login", "ANNA", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameAnswer() throws Exception {
        postJson("register", "anna", PASSWORD).andExpect(status().isCreated());

        postJson("login", "anna", "wrong password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
        postJson("login", "nobody", PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        postJson("register", "anna", "short").andExpect(status().isBadRequest());
    }

    @Test
    void passwordOver72BytesIsRejectedNotCrashed() throws Exception {
        postJson("register", "anna", "x".repeat(73)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("password_too_long"));
    }

    @Test
    void apiNeedsAToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        me("not.a.token").andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = loginToken();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        me(tampered).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        postJson("register", "anna", PASSWORD).andExpect(status().isCreated());
        String userId = users.findByUsername("anna").orElseThrow().getId().toString();
        Instant past = Instant.now().minus(Duration.ofDays(1));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId).issuedAt(past.minus(Duration.ofHours(1))).expiresAt(past).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        me(token).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfADeletedUserIsRejected() throws Exception {
        String token = loginToken();
        users.deleteAll();

        me(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unknown_user"));
    }

    @Test
    void healthStaysPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
