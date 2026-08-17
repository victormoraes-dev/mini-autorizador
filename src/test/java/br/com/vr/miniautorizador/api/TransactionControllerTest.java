package br.com.vr.miniautorizador.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import br.com.vr.miniautorizador.api.dto.AuthorizeTransactionRequest;
import br.com.vr.miniautorizador.api.dto.TransactionAuthorizationResponse;
import br.com.vr.miniautorizador.application.exception.CardNotFoundException;
import br.com.vr.miniautorizador.application.exception.TransactionDeniedException;
import br.com.vr.miniautorizador.application.port.in.AuthorizeTransactionUseCase;
import br.com.vr.miniautorizador.application.port.in.TransactionAuthorizationResult;

class TransactionControllerTest {

    private static final AuthorizeTransactionRequest REQUEST = new AuthorizeTransactionRequest(
            "7c97bca5-3c85-4a2d-aab8-2d06112b56e4",
            "1234",
            new BigDecimal("10.00"));

    @Test
    void returnsOkJsonWhenTransactionIsApproved() {
        ResponseEntity<TransactionAuthorizationResponse> response = controllerReturning(
                TransactionAuthorizationResult.APPROVED).authorize(REQUEST);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(TransactionAuthorizationResponse.authorized());
    }

    @Test
    void mapsMissingCardToApplicationException() {
        assertThatThrownBy(() -> controllerReturning(TransactionAuthorizationResult.CARD_NOT_FOUND)
                .authorize(REQUEST)).isInstanceOf(CardNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = TransactionAuthorizationResult.class,
            names = {"INVALID_PASSWORD", "INSUFFICIENT_BALANCE"})
    void mapsBusinessDenialToStructuredErrorException(TransactionAuthorizationResult result) {
        assertThatThrownBy(() -> controllerReturning(result).authorize(REQUEST))
                .isInstanceOfSatisfying(
                        TransactionDeniedException.class,
                        exception -> assertThat(exception.result()).isEqualTo(result));
    }

    private static TransactionController controllerReturning(TransactionAuthorizationResult result) {
        AuthorizeTransactionUseCase useCase = transaction -> result;
        return new TransactionController(useCase);
    }
}
