package io.github.akarp0508.koifolio.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent-tokens")
@Tag(name = "Agent tokens")
class AgentTokenController {

    private final AgentTokenService tokens;

    AgentTokenController(AgentTokenService tokens) {
        this.tokens = tokens;
    }

    @GetMapping
    @Operation(summary = "List your agent tokens")
    List<AgentTokenResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return tokens.list(userId(jwt)).stream().map(AgentTokenResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a token for an agent", description = """
            It works on the MCP server at /mcp and nowhere else. The secret is only ever returned here, so \
            keep it. A token works through expiresOn, or until revoked without one. Every token can read. Without \
            scopes it can also add transactions, which wait for your review, and edit companies, but not rules.""")
    CreatedAgentTokenResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AgentTokenRequest request) {
        var created = tokens.create(userId(jwt), request.name(), request.scopes(), request.expiresOn());
        return new CreatedAgentTokenResponse(AgentTokenResponse.of(created.token()), created.secret());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a token", description = "It stops working at once.")
    void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        tokens.revoke(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record AgentTokenRequest(@NotBlank @Size(max = 100) String name, Set<AgentScope> scopes, LocalDate expiresOn) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AgentTokenResponse(UUID id, String name, Set<AgentScope> scopes, LocalDate expiresOn, Instant lastUsedAt,
                              Instant createdAt) {

        static AgentTokenResponse of(AgentToken token) {
            return new AgentTokenResponse(token.getId(), token.getName(), token.getScopes(), token.getExpiresOn(),
                    token.getLastUsedAt(), token.getCreatedAt());
        }
    }

    record CreatedAgentTokenResponse(AgentTokenResponse token, String secret) {
    }
}
