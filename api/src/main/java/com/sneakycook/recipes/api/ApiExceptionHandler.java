package com.sneakycook.recipes.api;

import com.sneakycook.recipes.domain.InvalidCredentialsException;
import com.sneakycook.recipes.domain.RecipeNotFoundException;
import com.sneakycook.recipes.domain.UsernameTakenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Renders every error as an RFC 7807 problem document (contract schema
 * {@code Problem}) so clients see one error shape everywhere [REQ-1].
 * Validation failures additionally carry an {@code errors[]} array with one
 * {@code field}/{@code message} pair per violation (contract schema
 * {@code FieldError}).
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** [REQ-4] Unknown recipe id → 404 whose detail names the id. */
    @ExceptionHandler(RecipeNotFoundException.class)
    ProblemDetail recipeNotFound(RecipeNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /** [REQ-17] Registering an existing username → 409 whose detail names it. */
    @ExceptionHandler(UsernameTakenException.class)
    ProblemDetail usernameTaken(UsernameTakenException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** [REQ-17] Bad login credentials → 401; unknown user and wrong password are indistinguishable. */
    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    /** [REQ-2] Body failed Bean Validation → 400 with field errors. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request body failed validation", request);
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList());
        return problem;
    }

    /**
     * Constraint violations on query parameters (contract minima/maxima and
     * the sort pattern) → 400 with field errors. The generated interface is
     * {@code @Validated}, so these arrive as {@code ConstraintViolationException}
     * from the method-validation proxy.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidQueryParameters(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request parameters failed validation", request);
        problem.setProperty("errors", ex.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", lastNode(violation.getPropertyPath().toString()),
                        "message", violation.getMessage()))
                .toList());
        return problem;
    }

    private static String lastNode(String propertyPath) {
        return propertyPath.substring(propertyPath.lastIndexOf('.') + 1);
    }

    /** Constraint violations on query/path parameters → 400 with field errors. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail invalidParameters(HandlerMethodValidationException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request parameters failed validation", request);
        problem.setProperty("errors", ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", result.getMethodParameter().getParameterName(),
                                "message", String.valueOf(error.getDefaultMessage()))))
                .toList());
        return problem;
    }

    /**
     * Jackson parse and type errors — malformed JSON, wrong field types,
     * scalars where arrays belong — must speak RFC 7807 like every other
     * error [REQ-2]; Spring's default error body would not.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Request body is missing or malformed", request);
    }

    /**
     * Malformed path/query value (e.g. a non-UUID id) → 400 whose detail names
     * the parameter, the expected type, and the offending value — the wording
     * documented by the contract's {@code BadParameterProblem} example.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parameterTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Parameter '%s' must be %s, got '%s'"
                        .formatted(ex.getName(), expected(ex.getRequiredType()), ex.getValue()),
                request);
    }

    private static String expected(Class<?> requiredType) {
        if (requiredType == null) {
            return "a valid value";
        }
        if (requiredType == Integer.class || requiredType == int.class
                || requiredType == Long.class || requiredType == long.class) {
            return "an integer";
        }
        if (requiredType == Boolean.class || requiredType == boolean.class) {
            return "a boolean";
        }
        if (requiredType == UUID.class) {
            return "a UUID";
        }
        return "a valid " + requiredType.getSimpleName();
    }

    /**
     * Last-resort fallback (spec §6): every response speaks RFC 7807, even for
     * failures nothing anticipated [REQ-13]. Framework-raised HTTP errors
     * (415 unsupported media type, 405, unknown paths, …) implement
     * {@link ErrorResponse} and keep their status and safe detail; anything
     * else becomes a 500 whose detail deliberately says nothing about the
     * cause — the log line below carries the internals instead.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            ProblemDetail problem = errorResponse.getBody();
            HttpStatus status = HttpStatus.resolve(problem.getStatus());
            if (problem.getTitle() == null && status != null) {
                problem.setTitle(status.getReasonPhrase());
            }
            problem.setInstance(URI.create(request.getRequestURI()));
            return problem;
        }
        log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
