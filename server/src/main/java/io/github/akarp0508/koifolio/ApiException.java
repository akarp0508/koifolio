package io.github.akarp0508.koifolio;

import org.springframework.http.HttpStatus;

/** A failure the client should see, with a stable {@code code}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
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
