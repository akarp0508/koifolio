package io.github.akarp0508.koifolio;

import java.util.Map;

/** Fields that are invalid for reasons annotations can't express. Keyed by field name. */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public FieldValidationException(Map<String, String> errors) {
        super(errors.toString());
        this.errors = errors;
    }

    Map<String, String> errors() {
        return errors;
    }
}
