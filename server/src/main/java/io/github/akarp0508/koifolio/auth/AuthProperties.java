package io.github.akarp0508.koifolio.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("koifolio.auth")
public record AuthProperties(String jwtSecret, Duration tokenTtl, boolean registrationOpen) {
}
