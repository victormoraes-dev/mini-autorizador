package br.com.vr.miniautorizador.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.vr.miniautorizador.api.dto.AuthorizeTransactionRequest;
import br.com.vr.miniautorizador.application.port.in.AuthorizeTransactionUseCase;
import br.com.vr.miniautorizador.application.port.in.TransactionAuthorizationResult;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.model.Money;

@RestController
@RequestMapping("/transacoes")
public class TransactionController {

    private final AuthorizeTransactionUseCase authorizeTransactionUseCase;

    public TransactionController(AuthorizeTransactionUseCase authorizeTransactionUseCase) {
        this.authorizeTransactionUseCase = authorizeTransactionUseCase;
    }

    @PostMapping
    public ResponseEntity<String> authorize(@RequestBody AuthorizeTransactionRequest request) {
        TransactionAuthorizationResult result = authorizeTransactionUseCase.authorize(
                new CardNumber(request.numeroCartao()),
                new CardPassword(request.senhaCartao()),
                new Money(request.valor()));

        return switch (result) {
            case APPROVED -> ResponseEntity.status(HttpStatus.CREATED).body("OK");
            case CARD_NOT_FOUND -> denied("CARTAO_INEXISTENTE");
            case INVALID_PASSWORD -> denied("SENHA_INVALIDA");
            case INSUFFICIENT_BALANCE -> denied("SALDO_INSUFICIENTE");
        };
    }

    private static ResponseEntity<String> denied(String reason) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(reason);
    }
}
