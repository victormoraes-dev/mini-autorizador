package br.com.vr.miniautorizador.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import br.com.vr.miniautorizador.api.dto.CardResponse;
import br.com.vr.miniautorizador.api.dto.CreateCardRequest;
import br.com.vr.miniautorizador.application.port.in.CardDetails;
import br.com.vr.miniautorizador.application.port.in.CreateCardUseCase;
import br.com.vr.miniautorizador.application.port.in.GetCardUseCase;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;

@ExtendWith(MockitoExtension.class)
class CardControllerTest {

    private static final UUID ID = UUID.fromString("7c97bca5-3c85-4a2d-aab8-2d06112b56e4");
    private static final CardDetails DETAILS = new CardDetails(
            ID,
            "************4501",
            new BigDecimal("500.00"));

    @Mock
    private CreateCardUseCase createCardUseCase;

    @Mock
    private GetCardUseCase getCardUseCase;

    @InjectMocks
    private CardController controller;

    @Test
    void createsCardWithSafeRepresentationAndLocation() {
        CardNumber number = new CardNumber("6549873025634501");
        CardPassword password = new CardPassword("1234");
        when(createCardUseCase.create(number, password)).thenReturn(DETAILS);

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
        verify(createCardUseCase).create(number, password);
    }

    @Test
    void returnsStructuredCardByOpaqueId() {
        CardId cardId = CardId.from(ID.toString());
        when(getCardUseCase.get(cardId)).thenReturn(DETAILS);

        ResponseEntity<CardResponse> response = controller.get(ID.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(CardResponse.from(DETAILS));
        verify(getCardUseCase).get(cardId);
    }
}
