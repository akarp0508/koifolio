package io.github.akarp0508.koifolio;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.MismatchedInputException;

/** Makes request errors say what to fix, instead of Spring's generic messages. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail handle(ApiException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setProperty("code", e.code());
        return problem;
    }

    @ExceptionHandler
    ProblemDetail handle(FieldValidationException e) {
        return validationProblem(e.errors().entrySet().stream()
                .map(error -> fieldError(error.getKey(), error.getValue()))
                .sorted((a, b) -> a.get("field").compareTo(b.get("field")))
                .toList());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), String.valueOf(error.getDefaultMessage())))
                .sorted((a, b) -> a.get("field").compareTo(b.get("field")))
                .toList();
        return handleExceptionInternal(e, validationProblem(errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().getLast().getPropertyName();
            ProblemDetail problem = validationProblem(List.of(fieldError(field, "has an invalid value")));
            return handleExceptionInternal(e, problem, headers, status, request);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request body is missing or is not valid JSON");
        problem.setProperty("code", "invalid_body");
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message);
    }

    private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
        String detail = errors.stream()
                .map(error -> error.get("field") + " " + error.get("message"))
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("code", "validation_failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
