package io.github.akarp0508.koifolio.agent;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.auth.AuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AgentTokenService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final String PREFIX = "kf_";
    private static final int TOKEN_BYTES = 32;

    private final AgentTokenRepository tokens;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();

    AgentTokenService(AgentTokenRepository tokens, AuthService authService) {
        this.tokens = tokens;
        this.authService = authService;
    }

    /** @param secret shown once, since only its hash is stored */
    record Created(AgentToken token, String secret) {
    }

    @Transactional(readOnly = true)
    List<AgentToken> list(UUID userId) {
        return tokens.findByUserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional
    Created create(UUID userId, String name, LocalDate expiresOn) {
        authService.currentUser(userId);
        String trimmed = name.strip();
        if (tokens.existsByUserIdAndName(userId, trimmed)) {
            throw new ApiException(HttpStatus.CONFLICT, "agent_token_name_taken", "You already have a token with this name");
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Created(tokens.save(new AgentToken(userId, trimmed, hash(secret), expiresOn)), secret);
    }

    @Transactional
    void revoke(UUID userId, UUID id) {
        tokens.delete(tokens.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "agent_token_not_found", "Token not found")));
    }

    /** Empty unless the secret belongs to a token that hasn't expired. */
    @Transactional
    Optional<AgentCaller> authenticate(String secret) {
        return tokens.findByTokenHash(hash(secret))
                .filter(token -> !token.expired(LocalDate.now(ZONE)))
                .map(token -> {
                    token.used();
                    return new AgentCaller(token.getUserId(), token.getName());
                });
    }

    /** A plain hash is enough: the secret is long and random, so there's nothing to guess. */
    private static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
