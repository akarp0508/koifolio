package io.github.akarp0508.koifolio.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.of(authService.register(request.username(), request.password()));
    }

    @PostMapping("/login")
    AuthService.Token login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.of(authService.currentUser(UUID.fromString(jwt.getSubject())));
    }

    @ExceptionHandler
    ProblemDetail handle(AuthException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setProperty("code", e.code());
        return problem;
    }

    record RegisterRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(min = 8) String password) {
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record UserResponse(UUID id, String username) {

        static UserResponse of(User user) {
            return new UserResponse(user.getId(), user.getUsername());
        }
    }
}
