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
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** The official NBP API. Table A holds the mid rates of the main currencies, published each working day. */
@Component
class NbpFxProvider implements FxProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final RestClient client;

    NbpFxProvider(@Value("${koifolio.prices.nbp-url}") String baseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public List<Rate> latestRates() {
        JsonNode table = client.get().uri("/exchangerates/tables/A/").accept(MediaType.APPLICATION_JSON)
                .retrieve().body(JsonNode.class).path(0);
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
