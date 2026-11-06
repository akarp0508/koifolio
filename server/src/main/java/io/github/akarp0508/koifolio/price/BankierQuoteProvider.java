package io.github.akarp0508.koifolio.price;

import io.github.akarp0508.koifolio.domain.DailyClose;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

        Map<String, JsonNode> matches = new HashMap<>();
        for (JsonNode header : headers(candidates)) {
            if (ticker.equalsIgnoreCase(header.path("ticker").asString(""))) {
                matches.put(header.path("symbol").asString(), header);
            }
        }
        return candidates.stream().filter(matches::containsKey).findFirst()
                .flatMap(symbol -> {
                    String isin = matches.get(symbol).path("isin").asString(null);
                    return currency(symbol).map(currency -> new ResolvedSymbol(symbol, isin, currency));
                });
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

    /** History is split-adjusted, and today's row changes until the close, so {@code to} should be today at most. */
    @Override
    public List<DailyClose> dailyCloses(String symbol, String isin, LocalDate from, LocalDate to) {
        UriComponentsBuilder uri = isin == null
                ? uri("/foreign-company-section-chart/")
                : uri("/company-profile-chart/" + isin + "/");
        JsonNode chart = get(uri.queryParam("symbols", symbol)
                .queryParam("date_from", startOfDay(from))
                .queryParam("date_to", startOfDay(to)));

        JsonNode entry = chart.path("data").path(0);
        JsonNode rows = chart.path("main").isArray() ? chart.path("main")
                : entry.path("main").isArray() ? entry.path("main") : entry.path("data");

        List<DailyClose> closes = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode close = row.path(4);
            if (!close.isNumber()) {
                continue;
            }
            LocalDate date = Instant.ofEpochMilli(row.path(0).longValue()).atZone(ZoneOffset.UTC).toLocalDate();
            if (!date.isBefore(from) && date.isBefore(to)) {
                closes.add(new DailyClose(date, BigDecimal.valueOf(close.doubleValue())));
            }
        }
        return closes;
    }

    private static long startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
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
