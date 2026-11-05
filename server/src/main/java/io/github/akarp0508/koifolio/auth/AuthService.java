package io.github.akarp0508.koifolio.auth;

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

@Service
class AuthService {

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
        if (!properties.registrationOpen() && users.count() > 0) {
            throw new AuthException(HttpStatus.FORBIDDEN, "registration_closed", "Registration is closed");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "password_too_long",
                    "Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
        try {
            return users.saveAndFlush(new User(normalize(username), passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            throw new AuthException(HttpStatus.CONFLICT, "username_taken", "Username is already taken");
        }
    }

    Token login(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalize(username), password));
            return issueToken(authentication.getName());
        } catch (BadCredentialsException e) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Wrong username or password");
        }
    }

    User currentUser(UUID id) {
        return users.findById(id).orElseThrow(() ->
                new AuthException(HttpStatus.UNAUTHORIZED, "unknown_user", "This user no longer exists"));
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
