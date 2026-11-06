package io.github.akarp0508.koifolio.auth;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.price.FxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
        return new SettingsResponse(authService.currentUser(userId(jwt)).getBaseCurrency(), fx.rates().currencies());
    }

    @PatchMapping
    @Transactional
    @Operation(summary = "Change your base currency")
    SettingsResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SettingsRequest request) {
        List<String> currencies = fx.rates().currencies();
        if (!currencies.contains(request.baseCurrency())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unknown_currency", "No rate for " + request.baseCurrency());
        }
        authService.currentUser(userId(jwt)).setBaseCurrency(request.baseCurrency());
        return new SettingsResponse(request.baseCurrency(), currencies);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record SettingsRequest(@NotNull @Schema(example = "PLN") String baseCurrency) {
    }

    record SettingsResponse(String baseCurrency, List<String> currencies) {
    }
}
