package io.github.akarp0508.koifolio.price;

import io.github.akarp0508.koifolio.domain.DailyClose;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** Lets tests outside this package drive the fake price source. */
public class FakePrices {

    private final PriceTests.FakeProvider provider;

    FakePrices(PriceTests.FakeProvider provider) {
        this.provider = provider;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(PriceTests.FakeSource.class)
    public static class Config {

        @org.springframework.context.annotation.Bean
        FakePrices fakePrices(PriceTests.FakeProvider provider) {
            return new FakePrices(provider);
        }
    }

    public void reset() {
        provider.symbols.clear();
        provider.quotes.clear();
        provider.closes.clear();
        provider.historyCalls.clear();
        provider.failing = false;
    }

    public void symbol(String ticker, String symbol) {
        provider.symbols.put(ticker, new QuoteProvider.ResolvedSymbol(symbol, "ISIN", "PLN"));
    }

    public void closes(String symbol, List<DailyClose> closes) {
        provider.closes.put(symbol, closes);
    }
}
