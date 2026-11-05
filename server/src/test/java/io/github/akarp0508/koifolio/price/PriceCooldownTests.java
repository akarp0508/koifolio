package io.github.akarp0508.koifolio.price;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "koifolio.prices.refresh-cooldown=1h")
@ActiveProfiles("test")
@Import(TestDatabase.class)
class PriceCooldownTests {

    @Autowired
    private PriceService prices;

    @Test
    void repeatingARefreshTooSoonIsRefused() {
        prices.refreshNow();

        assertThatThrownBy(prices::refreshNow).isInstanceOf(ApiException.class);
    }
}
