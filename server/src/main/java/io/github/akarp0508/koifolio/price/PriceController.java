package io.github.akarp0508.koifolio.price;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prices")
@Tag(name = "Prices")
class PriceController {

    private final PriceService prices;

    PriceController(PriceService prices) {
        this.prices = prices;
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Fetch prices now", description = "They also update on a schedule. Repeating this too soon returns 429.")
    void refresh() {
        prices.refreshNow();
    }
}
