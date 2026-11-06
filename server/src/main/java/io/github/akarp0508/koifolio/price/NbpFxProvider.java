package io.github.akarp0508.koifolio.price;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** The official NBP API. Table A holds the mid rates of the main currencies, published each working day. */
@Component
class NbpFxProvider implements FxProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** NBP refuses longer ranges. */
    private static final int MAX_DAYS = 90;

    private final RestClient client;

    NbpFxProvider(@Value("${koifolio.prices.nbp-url}") String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public List<Rate> latestRates() {
        return read(get("/exchangerates/tables/A/").path(0));
    }

    @Override
    public List<Rate> ratesBetween(LocalDate from, LocalDate to) {
        List<Rate> rates = new ArrayList<>();
        for (LocalDate start = from; !start.isAfter(to); start = start.plusDays(MAX_DAYS)) {
            LocalDate end = start.plusDays(MAX_DAYS - 1);
            String path = "/exchangerates/tables/A/%s/%s/".formatted(start, end.isAfter(to) ? to : end);
            try {
                get(path).forEach(table -> rates.addAll(read(table)));
            } catch (HttpClientErrorException.NotFound e) {
                // NBP answers 404 for a range with no tables, such as a weekend
            }
        }
        return rates;
    }

    private JsonNode get(String path) {
        return client.get().uri(path).accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class);
    }

    private static List<Rate> read(JsonNode table) {
        LocalDate date = LocalDate.parse(table.path("effectiveDate").asString());
        List<Rate> rates = new ArrayList<>();
        for (JsonNode rate : table.path("rates")) {
            if (rate.path("code").isString() && rate.path("mid").isNumber()) {
                rates.add(new Rate(rate.path("code").asString(), new BigDecimal(rate.path("mid").asString()), date));
            }
        }
        return rates;
    }
}
