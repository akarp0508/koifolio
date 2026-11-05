package io.github.akarp0508.koifolio.auth;

import org.springframework.http.HttpStatus;

/** An auth failure the client should see, with a stable {@code code}. */
class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }
}
