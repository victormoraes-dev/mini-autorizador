package br.com.vr.miniautorizador.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import br.com.vr.miniautorizador.application.exception.TransactionDeniedException;
import br.com.vr.miniautorizador.application.port.in.TransactionAuthorizationResult;

class HttpErrorHandlerTest {

    private final HttpErrorHandler handler = new HttpErrorHandler();

    @Test
    void returnsConflictProblemForDuplicateCard() {
        MockHttpServletRequest request = request("POST", "/api/v1/cards");

        ResponseEntity<ProblemDetail> response = handler.handleCardAlreadyExists(request);

        assertProblem(response, HttpStatus.CONFLICT, "CARD_ALREADY_EXISTS", "/api/v1/cards");
    }

    @Test
    void returnsStableProblemCodesForTransactionDenials() {
        MockHttpServletRequest request = request("POST", "/api/v1/transactions");

        ResponseEntity<ProblemDetail> invalidPassword = handler.handleTransactionDenied(
                new TransactionDeniedException(TransactionAuthorizationResult.INVALID_PASSWORD),
                request);
        ResponseEntity<ProblemDetail> insufficientBalance = handler.handleTransactionDenied(
                new TransactionDeniedException(TransactionAuthorizationResult.INSUFFICIENT_BALANCE),
                request);

        assertProblem(invalidPassword, HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_PASSWORD", request.getRequestURI());
        assertProblem(
                insufficientBalance,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "INSUFFICIENT_BALANCE",
                request.getRequestURI());
    }

    @Test
    void hidesInvalidValueFromProblemDetail() {
        MockHttpServletRequest request = request("GET", "/api/v1/cards/not-a-uuid");

        ResponseEntity<ProblemDetail> response = handler.handleInvalidValue(request);

        assertProblem(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", request.getRequestURI());
        assertThat(response.getBody().getDetail()).doesNotContain("not-a-uuid");
    }

    @Test
    void sanitizesUnexpectedErrors() {
        MockHttpServletRequest request = request("POST", "/api/v1/transactions");

        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(
                new IllegalStateException("database-secret"),
                request);

        assertProblem(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", request.getRequestURI());
        assertThat(response.getBody().getDetail()).doesNotContain("database-secret");
    }

    @Test
    void preservesNotFoundForUnknownResources() {
        MockHttpServletRequest request = request("GET", "/unknown");

        ResponseEntity<ProblemDetail> response = handler.handleResourceNotFound(request);

        assertProblem(response, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", request.getRequestURI());
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private static void assertProblem(
            ResponseEntity<ProblemDetail> response,
            HttpStatus status,
            String code,
            String instance) {

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(status.value());
        assertThat(response.getBody().getInstance().toString()).isEqualTo(instance);
        assertThat(response.getBody().getProperties()).containsEntry("code", code);
    }
}
