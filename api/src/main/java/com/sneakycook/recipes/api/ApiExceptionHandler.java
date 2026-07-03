package com.sneakycook.recipes.api;

import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.springmvc.InvalidRequestException;
import com.sneakycook.recipes.domain.RecipeNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders every error as an RFC 7807 problem document (contract schema
 * {@code Problem}) so clients see one error shape everywhere [REQ-1].
 * Validation failures additionally carry an {@code errors[]} array with one
 * {@code field}/{@code message} pair per violation (contract schema
 * {@code FieldError}).
 */
@RestControllerAdvice
class ApiExceptionHandler {

    /** [REQ-4] Unknown recipe id → 404 whose detail names the id. */
    @ExceptionHandler(RecipeNotFoundException.class)
    ProblemDetail recipeNotFound(RecipeNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request);
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
     * Request body or parameters violate the OpenAPI contract [REQ-2] — wrong
     * wire types (e.g. {@code servings: 4.5} where the spec declares integer),
     * missing required properties, or schema shape mismatches.
     */
    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidContract(InvalidRequestException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request does not match the API contract", request);
        problem.setProperty("errors", ex.getValidationReport().getMessages().stream()
                .filter(message -> message.getLevel() == ValidationReport.Level.ERROR)
                .map(message -> Map.of(
                        "field", contractField(message),
                        "message", message.getMessage()))
                .toList());
        return problem;
    }

    private static final Pattern BRACKETED_FIELDS = Pattern.compile("\\[([^\\]]+)\\]");
    private static final Pattern QUOTED_PROPERTY = Pattern.compile("'([^']+)'");

    private static String contractField(ValidationReport.Message message) {
        Optional<String> fromPointer = message.getContext()
                .flatMap(context -> context.getPointers().map(pointers -> jsonPointerToField(pointers.getInstance())))
                .filter(field -> !field.isBlank());

        if (fromPointer.isPresent()) {
            return fromPointer.get();
        }

        return extractFieldFromMessage(message.getMessage()).orElse(message.getKey());
    }

    private static String jsonPointerToField(String pointer) {
        if (pointer == null || pointer.isBlank()) {
            return "";
        }
        String trimmed = pointer.startsWith("/") ? pointer.substring(1) : pointer;
        int slash = trimmed.indexOf('/');
        return slash >= 0 ? trimmed.substring(0, slash) : trimmed;
    }

    /**
     * Parses common OpenAPI schema violation wordings, e.g.
     * {@code required property 'name' not found} or
     * {@code Object has missing required properties ([name])}.
     */
    private static Optional<String> extractFieldFromMessage(String message) {
        Matcher quoted = QUOTED_PROPERTY.matcher(message);
        if (quoted.find()) {
            return Optional.of(quoted.group(1));
        }
        Matcher bracketed = BRACKETED_FIELDS.matcher(message);
        if (bracketed.find()) {
            return Optional.of(bracketed.group(1).split(",")[0].trim());
        }
        return Optional.empty();
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

    /** Malformed path/query value (e.g. a non-UUID id) → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parameterTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Parameter '%s' has an invalid value".formatted(ex.getName()),
                request);
    }

    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
