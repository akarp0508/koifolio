package io.github.akarp0508.koifolio.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.akarp0508.koifolio.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "koifolio.auth.registration-open=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabase.class)
class OpenRegistrationTests {

    @Autowired
    private MockMvc mvc;

    private ResultActions register(String username) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"correct horse\"}".formatted(username)));
    }

    @Test
    void anyoneCanRegisterButUsernamesAreUnique() throws Exception {
        register("anna").andExpect(status().isCreated());
        register("bob").andExpect(status().isCreated());

        register("anna").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("username_taken"));
    }
}
