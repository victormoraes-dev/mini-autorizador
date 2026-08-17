package br.com.vr.miniautorizador.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import br.com.vr.miniautorizador.api.dto.CardResponse;
import br.com.vr.miniautorizador.api.dto.CreateCardRequest;
import br.com.vr.miniautorizador.application.port.in.CardDetails;
import br.com.vr.miniautorizador.application.port.in.CreateCardUseCase;
import br.com.vr.miniautorizador.application.port.in.GetCardUseCase;

class CardControllerTest {

    private static final UUID ID = UUID.fromString("7c97bca5-3c85-4a2d-aab8-2d06112b56e4");
    private static final CardDetails DETAILS = new CardDetails(
            ID,
            "************4501",
            new BigDecimal("500.00"));

    @Test
    void createsCardWithSafeRepresentationAndLocation() {
        CardController controller = controller();

        ResponseEntity<CardResponse> response = controller.create(
                new CreateCardRequest("6549873025634501", "1234"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/api/v1/cards/" + ID));
        assertThat(response.getBody()).isEqualTo(new CardResponse(
                ID,
                "************4501",
                new BigDecimal("500.00")));
        assertThat(response.getBody().toString()).doesNotContain("6549873025634501", "1234");
    }

    @Test
    void returnsStructuredCardByOpaqueId() {
        ResponseEntity<CardResponse> response = controller().get(ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(CardResponse.from(DETAILS));
    }

    private static CardController controller() {
        CreateCardUseCase create = (cardNumber, password) -> DETAILS;
        GetCardUseCase get = cardId -> DETAILS;
        return new CardController(create, get);
    }
}
