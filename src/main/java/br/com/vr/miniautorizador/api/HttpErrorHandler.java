package br.com.vr.miniautorizador.api;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.validation.method.ParameterErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.vr.miniautorizador.application.exception.CardAlreadyExistsException;
import br.com.vr.miniautorizador.application.exception.CardNotFoundException;
import br.com.vr.miniautorizador.application.exception.TransactionDeniedException;
import br.com.vr.miniautorizador.application.port.in.TransactionAuthorizationResult;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpErrorHandler.class);

    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleCardNotFound(HttpServletRequest request) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                "card-not-found",
                "Card not found",
                "The requested card does not exist",
                "CARD_NOT_FOUND",
                request));
    }

    @ExceptionHandler(CardAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleCardAlreadyExists(HttpServletRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                "card-already-exists",
                "Card already exists",
                "A card with the supplied number already exists",
                "CARD_ALREADY_EXISTS",
                request));
    }

    @ExceptionHandler(TransactionDeniedException.class)
    public ResponseEntity<ProblemDetail> handleTransactionDenied(
            TransactionDeniedException exception,
            HttpServletRequest request) {
        Denial denial = Denial.from(exception.result());
        return response(problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                denial.type(),
                "Transaction denied",
                denial.detail(),
                denial.code(),
                request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Request validation failed",
                "One or more request fields are invalid",
                "VALIDATION_ERROR",
                request);
        List<Map<String, String>> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("violations", violations);
        return response(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        ProblemDetail problem = validationProblem(request);
        List<Map<String, String>> violations = exception.getParameterValidationResults().stream()
                .filter(ParameterErrors.class::isInstance)
                .map(ParameterErrors.class::cast)
                .flatMap(errors -> errors.getFieldErrors().stream())
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("violations", violations);
        return response(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedRequest(HttpServletRequest request) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "malformed-request",
                "Malformed request",
                "The request body is missing or cannot be parsed",
                "MALFORMED_REQUEST",
                request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidValue(HttpServletRequest request) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                "invalid-value",
                "Invalid request value",
                "A request value has an invalid format",
                "VALIDATION_ERROR",
                request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("Unexpected error while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
        return response(problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-server-error",
                "Internal server error",
                "An unexpected error occurred",
                "INTERNAL_SERVER_ERROR",
                request));
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String type,
            String title,
            String detail,
            String code,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:problem:" + type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }

    private static ProblemDetail validationProblem(HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "validation-error",
                "Request validation failed",
                "One or more request fields are invalid",
                "VALIDATION_ERROR",
                request);
    }

    private static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private record Denial(String type, String code, String detail) {

        private static Denial from(TransactionAuthorizationResult result) {
            return switch (result) {
                case INVALID_PASSWORD -> new Denial(
                        "invalid-password",
                        "INVALID_PASSWORD",
                        "The supplied card password is invalid");
                case INSUFFICIENT_BALANCE -> new Denial(
                        "insufficient-balance",
                        "INSUFFICIENT_BALANCE",
                        "The card does not have enough balance");
                case APPROVED, CARD_NOT_FOUND -> throw new IllegalArgumentException(
                        "Result does not represent an unprocessable transaction");
            };
        }
    }
}
