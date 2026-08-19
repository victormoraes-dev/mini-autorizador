package br.com.vr.miniautorizador.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.vr.miniautorizador.api.dto.CardResponse;
import br.com.vr.miniautorizador.api.dto.CreateCardRequest;
import br.com.vr.miniautorizador.application.exception.CardAlreadyExistsException;
import br.com.vr.miniautorizador.application.exception.CardNotFoundException;
import br.com.vr.miniautorizador.application.port.in.CardDetails;
import br.com.vr.miniautorizador.application.port.in.CreateCardUseCase;
import br.com.vr.miniautorizador.application.port.in.GetCardUseCase;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;

@RestController
@RequestMapping("/cartoes")
public class CardController {

    private final CreateCardUseCase createCardUseCase;
    private final GetCardUseCase getCardUseCase;

    public CardController(CreateCardUseCase createCardUseCase, GetCardUseCase getCardUseCase) {
        this.createCardUseCase = createCardUseCase;
        this.getCardUseCase = getCardUseCase;
    }

    @PostMapping
    public ResponseEntity<CardResponse> create(@RequestBody CreateCardRequest request) {

        CardResponse response = new CardResponse(request.senha(), request.numeroCartao());

        try {
            createCardUseCase.create(
                    new CardNumber(request.numeroCartao()),
                    new CardPassword(request.senha()));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (CardAlreadyExistsException exception) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(response);
        }
    }

    @GetMapping("/{numeroCartao}")
    public ResponseEntity<String> getBalance(@PathVariable String numeroCartao) {

        try {
            CardDetails card = getCardUseCase.get(new CardNumber(numeroCartao));

            return ResponseEntity.ok(card.balance().toPlainString());
        } catch (CardNotFoundException exception) {
            return ResponseEntity.notFound().build();
        }
    }
}
