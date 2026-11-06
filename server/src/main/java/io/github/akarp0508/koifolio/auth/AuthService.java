package io.github.akarp0508.koifolio.auth;

import io.github.akarp0508.koifolio.ApiException;
import io.github.akarp0508.koifolio.domain.CostFxRule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** BCrypt only looks at the first 72 bytes, and Spring refuses anything longer. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                JwtEncoder jwtEncoder, AuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    User register(String username, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "password_too_long",
                    "Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
        try {
            return users.saveAndFlush(new User(normalize(username), passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "username_taken", "Username is already taken");
        }
    }

    Token login(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalize(username), password));
            return issueToken(authentication.getName());
        } catch (BadCredentialsException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Wrong username or password");
        }
    }

    /** The user a token belongs to. Tokens outlive deleted users, so this can fail. */
    /** Sets what an export carried. The base currency must already be known to have a rate. */
    @Transactional
    public void restoreSettings(UUID userId, String baseCurrency, CostFxRule costFxRule) {
        User user = currentUser(userId);
        user.setBaseCurrency(baseCurrency);
        user.setCostFxRule(costFxRule);
    }

    public User currentUser(UUID id) {
        return users.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "unknown_user", "This user no longer exists"));
    }

    private Token issueToken(String userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("koifolio")
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(properties.tokenTtl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new Token(value, properties.tokenTtl().toSeconds());
    }

    private static String normalize(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    record Token(String accessToken, long expiresIn) {
    }
}
