package io.github.akarp0508.koifolio.price;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty("koifolio.prices.schedule-enabled")
class PriceScheduler {

    private final PriceService prices;

    PriceScheduler(PriceService prices) {
        this.prices = prices;
    }

    @Scheduled(initialDelayString = "${koifolio.prices.initial-delay}", fixedDelayString = "${koifolio.prices.refresh-every}")
    void refresh() {
        prices.refresh();
    }
}
