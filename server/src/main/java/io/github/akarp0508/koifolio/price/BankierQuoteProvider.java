package io.github.akarp0508.koifolio.price;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Bankier's public quote endpoints. They're undocumented, so see docs/price-sources.md, and every
 * response is read defensively. A search doesn't give tickers for Polish companies, so resolving
 * fetches quotes for the candidates, which do carry the real ticker.
 */
@Component
class BankierQuoteProvider implements QuoteProvider {

    private static final int BATCH_SIZE = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** Most specific first: the same ticker can exist on several markets. */
    private static final List<String> SEARCH_GROUPS = List.of("companies", "etfs", "foreign_companies");

    private final RestClient client;

    BankierQuoteProvider(@Value("${koifolio.prices.bankier-url}") String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", "koifolio").build();
    }

    @Override
    public Optional<ResolvedSymbol> resolve(String ticker) {
        JsonNode search = get(uri("/instrument-search/").queryParam("bankier", "true").queryParam("query", ticker));
        Set<String> candidates = new LinkedHashSet<>();
        for (String group : SEARCH_GROUPS) {
            for (JsonNode item : search.path(group)) {
                String symbol = item.path("symbol").asString(item.path("ticker").asString(""));
                if (!symbol.isEmpty()) {
                    candidates.add(symbol);
                }
            }
        }

        Set<String> matches = new LinkedHashSet<>();
        for (JsonNode header : headers(candidates)) {
            if (ticker.equalsIgnoreCase(header.path("ticker").asString(""))) {
                matches.add(header.path("symbol").asString());
            }
        }
        return candidates.stream().filter(matches::contains).findFirst()
                .flatMap(symbol -> currency(symbol).map(currency -> new ResolvedSymbol(symbol, currency)));
    }

    @Override
    public Map<String, Quote> quotes(Collection<String> symbols) {
        Map<String, Quote> quotes = new HashMap<>();
        for (JsonNode header : headers(symbols)) {
            String symbol = header.path("symbol").asString("");
            JsonNode price = header.path("price");
            JsonNode time = header.path("date_time");
            if (!symbol.isEmpty() && price.isNumber() && time.isNumber()) {
                quotes.put(symbol, new Quote(BigDecimal.valueOf(price.doubleValue()),
                        Instant.ofEpochMilli(time.longValue())));
            }
        }
        return quotes;
    }

    private List<JsonNode> headers(Collection<String> symbols) {
        List<String> all = new ArrayList<>(symbols);
        List<JsonNode> headers = new ArrayList<>();
        for (int start = 0; start < all.size(); start += BATCH_SIZE) {
            String batch = String.join(",", all.subList(start, Math.min(start + BATCH_SIZE, all.size())));
            get(uri("/instrument-header/").queryParam("symbol", batch)).forEach(headers::add);
        }
        return headers;
    }

    private Optional<String> currency(String symbol) {
        JsonNode chart = get(uri("/investor-chart/").queryParam("symbols", symbol));
        return Optional.of(chart.path("data").path(0).path("currency").asString("")).filter(code -> !code.isEmpty());
    }

    private static UriComponentsBuilder uri(String path) {
        return UriComponentsBuilder.fromPath(path);
    }

    private JsonNode get(UriComponentsBuilder uri) {
        URI relative = uri.encode().build().toUri();
        return client.get().uri(relative.toString()).retrieve().body(JsonNode.class);
    }
}
