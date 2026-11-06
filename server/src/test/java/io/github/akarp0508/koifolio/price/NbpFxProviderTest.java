package io.github.akarp0508.koifolio.price;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NbpFxProviderTest {

    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/exchangerates/tables/A/", exchange -> {
            byte[] body = """
                    [{"table":"A","effectiveDate":"2026-11-06",
                      "rates":[{"currency":"dolar","code":"USD","mid":3.6512},{"currency":"jen","code":"JPY","mid":0.024}]}]"""
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/exchangerates/tables/A/2026-11-07/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aRangeWithNoTablesIsEmpty() {
        var rates = new NbpFxProvider("http://localhost:" + server.getAddress().getPort())
                .ratesBetween(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8));

        assertThat(rates).isEmpty();
    }

    @Test
    void readsTheLatestTable() {
        var rates = new NbpFxProvider("http://localhost:" + server.getAddress().getPort()).latestRates();

        assertThat(rates).containsExactly(
                new FxProvider.Rate("USD", new BigDecimal("3.6512"), LocalDate.of(2026, 11, 6)),
                new FxProvider.Rate("JPY", new BigDecimal("0.024"), LocalDate.of(2026, 11, 6)));
    }
}
