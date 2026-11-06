package io.github.akarp0508.koifolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

class SwaggerTests {

    @Nested
    @SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @Import(TestDatabase.class)
    class Enabled {

        @Autowired
        private MockMvc mvc;

        @Test
        void specListsTheApiAndItsAuth() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                    .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get").exists())
                    .andExpect(jsonPath("$.security[0].bearer").exists())
                    .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
        }

        @Test
        void requestTypesKeepTheirOwnSchemas() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.schemas.CreateAccountRequest.properties.currency").exists())
                    .andExpect(jsonPath("$.components.schemas.CreateInstrumentRequest.properties.ticker").exists());
        }

        /** springdoc silently merges records with the same simple name, so the spec shows the wrong fields. */
        @Test
        void noTwoControllersShareARecordName() {
            Map<String, List<String>> byName = new ClassPathScanningCandidateComponentProvider(false) {
                {
                    addIncludeFilter((reader, factory) -> reader.getClassMetadata().isIndependent()
                            && reader.getClassMetadata().getClassName().contains("Controller$"));
                }
            }.findCandidateComponents("io.github.akarp0508.koifolio").stream()
                    .map(BeanDefinition::getBeanClassName)
                    .collect(Collectors.groupingBy(name -> name.substring(name.lastIndexOf('$') + 1)));

            assertThat(byName.values()).allSatisfy(names -> assertThat(names).hasSize(1));
        }

        @Test
        void uiIsServed() throws Exception {
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @Import(TestDatabase.class)
    class DisabledByDefault {

        @Autowired
        private MockMvc mvc;

        @Test
        void specIsNotAvailable() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        }
    }
}
