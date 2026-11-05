package io.github.akarp0508.koifolio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestDatabase.class)
class KoifolioApplicationTests {

    @Test
    void contextLoads() {
    }
}
