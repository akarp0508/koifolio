package io.github.akarp0508.koifolio.auth;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.domain.CostFxRule;
import io.github.akarp0508.koifolio.price.FxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Settings")
class SettingsController {

    private final AuthService authService;
    private final FxService fx;

    SettingsController(AuthService authService, FxService fx) {
        this.authService = authService;
        this.fx = fx;
    }

    @GetMapping
    @Operation(summary = "Your settings, with the currencies that can be used as a base")
    SettingsResponse get(@AuthenticationPrincipal Jwt jwt) {
        return SettingsResponse.of(authService.currentUser(userId(jwt)), fx.rates().currencies());
    }

    @PatchMapping
    @Transactional
    @Operation(summary = "Change your settings", description = "Send only what changes.")
    SettingsResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SettingsRequest request) {
        List<String> currencies = fx.rates().currencies();
        User user = authService.currentUser(userId(jwt));
        if (request.baseCurrency() != null) {
            if (!currencies.contains(request.baseCurrency())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "unknown_currency", "No rate for " + request.baseCurrency());
            }
            user.setBaseCurrency(request.baseCurrency());
        }
        if (request.costFxRule() != null) {
            user.setCostFxRule(request.costFxRule());
        }
        return SettingsResponse.of(user, currencies);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record SettingsRequest(@Schema(example = "PLN") String baseCurrency, CostFxRule costFxRule) {
    }

    /** @param costFxRule which day's rate converts a trade to the base currency */
    record SettingsResponse(String baseCurrency, CostFxRule costFxRule, List<String> currencies) {

        static SettingsResponse of(User user, List<String> currencies) {
            return new SettingsResponse(user.getBaseCurrency(), user.getCostFxRule(), currencies);
        }
    }
}
