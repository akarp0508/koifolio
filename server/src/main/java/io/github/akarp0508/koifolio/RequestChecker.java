package io.github.akarp0508.koifolio;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Applies a request's validation annotations where {@code @Valid} doesn't run, as in MCP tools and restores. */
@Component
public class RequestChecker {

    private final Validator validator;

    RequestChecker(Validator validator) {
        this.validator = validator;
    }

    public void check(Object request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations.stream().collect(Collectors.toMap(
                    violation -> violation.getPropertyPath().toString(), ConstraintViolation::getMessage, (first, next) -> first)));
        }
    }
}
