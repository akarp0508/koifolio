package io.github.akarp0508.koifolio.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Create a user", description = "Only the first user can register, unless registration is open.")
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.of(authService.register(request.username(), request.password()));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in and get an access token")
    AuthService.Token login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @GetMapping("/me")
    @Operation(summary = "Get the logged-in user")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.of(authService.currentUser(UUID.fromString(jwt.getSubject())));
    }

    record RegisterRequest(
            @NotBlank @Size(max = 50, message = "must be at most 50 characters") String username,
            @NotNull @Size(min = 8, message = "must be at least 8 characters") String password) {
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record UserResponse(UUID id, String username) {

        static UserResponse of(User user) {
            return new UserResponse(user.getId(), user.getUsername());
        }
    }
}
