package io.github.akarp0508.koifolio.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs against a local server answering in the shapes the real source returned on 2026-11-05. */
class BankierQuoteProviderTest {

    private final Map<String, String> responses = new HashMap<>();
    private final List<String> requests = new ArrayList<>();
    private int status = 200;
    private HttpServer server;
    private BankierQuoteProvider provider;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String query = URLDecoder.decode(String.valueOf(exchange.getRequestURI().getRawQuery()), StandardCharsets.UTF_8);
            String key = exchange.getRequestURI().getPath() + "?" + query;
            requests.add(key);
            byte[] body = responses.getOrDefault(key, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        provider = new BankierQuoteProvider("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void search(String ticker, String body) {
        responses.put("/instrument-search/?bankier=true&query=" + ticker, body);
    }

    private void header(String symbols, String body) {
        responses.put("/instrument-header/?symbol=" + symbols, body);
    }

    private void currency(String symbol, String code) {
        responses.put("/investor-chart/?symbols=" + symbol, "{\"data\":[{\"symbol\":\"%s\",\"currency\":\"%s\"}]}".formatted(symbol, code));
    }

    @Test
    void resolvesAPolishCompanyByMatchingTheTickerInItsQuote() {
        search("XTB", "{\"companies\":[{\"isin\":\"a\",\"symbol\":\"NEXTBIKE\"},{\"isin\":\"b\",\"symbol\":\"XTB\"}],\"funds_tfi\":[{}]}");
        header("NEXTBIKE,XTB", "[{\"symbol\":\"NEXTBIKE\",\"ticker\":\"NXB\",\"price\":1.0,\"date_time\":1},{\"symbol\":\"XTB\",\"ticker\":\"XTB\",\"price\":2.0,\"date_time\":1}]");
        currency("XTB", "PLN");

        assertThat(provider.resolve("XTB")).contains(new QuoteProvider.ResolvedSymbol("XTB", "PLN"));
    }

    @Test
    void resolvesWhereTheSymbolDiffersFromTheTicker() {
        search("PKO", "{\"companies\":[{\"symbol\":\"PKOBP\"},{\"symbol\":\"PKOBP-PP\"}]}");
        header("PKOBP,PKOBP-PP", "[{\"symbol\":\"PKOBP\",\"ticker\":\"PKO\",\"price\":1.0,\"date_time\":1}]");
        currency("PKOBP", "PLN");

        assertThat(provider.resolve("PKO")).contains(new QuoteProvider.ResolvedSymbol("PKOBP", "PLN"));
    }

    @Test
    void resolvesAForeignCompanyIgnoringCase() {
        search("SIEGN", "{\"foreign_companies\":[{\"symbol\":\"SIEMENS\",\"ticker\":\"SIEGn\",\"market\":\"Deutsche Börse\"}]}");
        header("SIEMENS", "[{\"symbol\":\"SIEMENS\",\"ticker\":\"SIEGn\",\"price\":273.25,\"date_time\":1793896440000.0}]");
        currency("SIEMENS", "EUR");

        assertThat(provider.resolve("SIEGN")).contains(new QuoteProvider.ResolvedSymbol("SIEMENS", "EUR"));
    }

    @Test
    void resolvesAnEtfWhichOnlyHasATicker() {
        search("ETFBW20TR", "{\"etfs\":[{\"isin\":\"a\",\"ticker\":\"ETFBW20TR\"}]}");
        header("ETFBW20TR", "[{\"symbol\":\"ETFBW20TR\",\"ticker\":\"ETFBW20TR\",\"price\":86.91,\"date_time\":1,\"type\":null}]");
        currency("ETFBW20TR", "PLN");

        assertThat(provider.resolve("ETFBW20TR")).contains(new QuoteProvider.ResolvedSymbol("ETFBW20TR", "PLN"));
    }

    @Test
    void prefersAPolishCompanyWhenATickerExistsOnSeveralMarkets() {
        search("KO", "{\"companies\":[{\"symbol\":\"KOPOLSKA\"}],\"foreign_companies\":[{\"symbol\":\"COCACOLA\",\"ticker\":\"KO\"}]}");
        header("KOPOLSKA,COCACOLA", "[{\"symbol\":\"COCACOLA\",\"ticker\":\"KO\",\"price\":1.0,\"date_time\":1},{\"symbol\":\"KOPOLSKA\",\"ticker\":\"KO\",\"price\":2.0,\"date_time\":1}]");
        currency("KOPOLSKA", "PLN");

        assertThat(provider.resolve("KO")).contains(new QuoteProvider.ResolvedSymbol("KOPOLSKA", "PLN"));
    }

    @Test
    void aSimilarTickerIsNotAMatch() {
        search("PAS", "{\"companies\":[{\"symbol\":\"PASSUS2\"}]}");
        header("PASSUS2", "[{\"symbol\":\"PASSUS2\",\"ticker\":\"PAS2\",\"price\":1.0,\"date_time\":1}]");

        assertThat(provider.resolve("PAS")).isEmpty();
    }

    @Test
    void unknownTickerResolvesToNothing() {
        search("NOPE", "{\"companies\":[],\"funds_tfi\":[]}");

        assertThat(provider.resolve("NOPE")).isEmpty();
        assertThat(requests).hasSize(1);
    }

    @Test
    void noCurrencyMeansNotResolved() {
        search("XTB", "{\"companies\":[{\"symbol\":\"XTB\"}]}");
        header("XTB", "[{\"symbol\":\"XTB\",\"ticker\":\"XTB\",\"price\":1.0,\"date_time\":1}]");

        assertThat(provider.resolve("XTB")).isEmpty();
    }

    @Test
    void readsQuotesIncludingFloatTimestamps() {
        header("PKOBP,APPLE", """
                [{"symbol":"PKOBP","ticker":"PKO","price":116.6,"date_time":1793894340000},
                 {"symbol":"APPLE","ticker":"AAPL","price":344.17,"date_time":1793912340000.0}]""");

        Map<String, QuoteProvider.Quote> quotes = provider.quotes(List.of("PKOBP", "APPLE"));

        assertThat(quotes.get("PKOBP").price()).isEqualByComparingTo(new BigDecimal("116.6"));
        assertThat(quotes.get("PKOBP").quotedAt()).isEqualTo(Instant.ofEpochMilli(1793894340000L));
        assertThat(quotes.get("APPLE").quotedAt()).isEqualTo(Instant.ofEpochMilli(1793912340000L));
    }

    @Test
    void leavesOutQuotesWithoutAPrice() {
        header("XTB", "[{\"symbol\":\"XTB\",\"ticker\":\"XTB\",\"price\":null,\"date_time\":1}]");

        assertThat(provider.quotes(List.of("XTB"))).isEmpty();
    }

    @Test
    void asksInBatches() {
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            symbols.add("S" + i);
        }

        provider.quotes(symbols);

        assertThat(requests).hasSize(3);
    }

    @Test
    void aServerErrorIsThrownForTheCallerToHandle() {
        status = 500;

        assertThatThrownBy(() -> provider.quotes(List.of("XTB"))).isInstanceOf(RuntimeException.class);
    }
}
