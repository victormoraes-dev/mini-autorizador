package br.com.vr.miniautorizador.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.vr.miniautorizador.api.dto.AuthorizeTransactionRequest;
import br.com.vr.miniautorizador.api.dto.ProblemResponse;
import br.com.vr.miniautorizador.api.dto.TransactionAuthorizationResponse;
import br.com.vr.miniautorizador.application.exception.CardNotFoundException;
import br.com.vr.miniautorizador.application.exception.TransactionDeniedException;
import br.com.vr.miniautorizador.application.port.in.AuthorizeTransactionUseCase;
import br.com.vr.miniautorizador.application.port.in.TransactionAuthorizationResult;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.model.Money;
import br.com.vr.miniautorizador.domain.model.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Card transaction authorization")
public class TransactionController {

        private final AuthorizeTransactionUseCase authorizeTransactionUseCase;

        public TransactionController(AuthorizeTransactionUseCase authorizeTransactionUseCase) {
                this.authorizeTransactionUseCase = authorizeTransactionUseCase;
        }

        @PostMapping
        @Operation(summary = "Authorize and atomically debit a card transaction")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Transaction authorized"),
                        @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Card not found", content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
                        @ApiResponse(responseCode = "422", description = "Transaction denied", content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Missing or invalid API key", content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
                        @ApiResponse(responseCode = "403", description = "Writer permission required", content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
                        @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
                        @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(schema = @Schema(implementation = ProblemResponse.class)))
        })
        public ResponseEntity<TransactionAuthorizationResponse> authorize(
                        @Valid @RequestBody AuthorizeTransactionRequest request) {

                Transaction transaction = Transaction.request(
                                CardId.from(request.cardId()),
                                new CardPassword(request.password()),
                                new Money(request.amount()));

                TransactionAuthorizationResult result = authorizeTransactionUseCase.authorize(transaction);

                return switch (result) {
                        case APPROVED -> ResponseEntity.ok(TransactionAuthorizationResponse.authorized());
                        case CARD_NOT_FOUND -> throw new CardNotFoundException();
                        case INVALID_PASSWORD, INSUFFICIENT_BALANCE -> throw new TransactionDeniedException(result);
                };
        }
}
