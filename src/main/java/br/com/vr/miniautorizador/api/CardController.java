package br.com.vr.miniautorizador.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.vr.miniautorizador.api.dto.CardResponse;
import br.com.vr.miniautorizador.api.dto.CreateCardRequest;
import br.com.vr.miniautorizador.api.dto.ProblemResponse;
import br.com.vr.miniautorizador.application.port.in.CardDetails;
import br.com.vr.miniautorizador.application.port.in.CreateCardUseCase;
import br.com.vr.miniautorizador.application.port.in.GetCardUseCase;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards", description = "Card creation and balance operations")
public class CardController {

    private final CreateCardUseCase createCardUseCase;
    private final GetCardUseCase getCardUseCase;

    public CardController(CreateCardUseCase createCardUseCase, GetCardUseCase getCardUseCase) {
        this.createCardUseCase = createCardUseCase;
        this.getCardUseCase = getCardUseCase;
    }

    @PostMapping
    @Operation(summary = "Create a card with an initial balance of 500.00")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Card created"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Card already exists",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
            @ApiResponse(responseCode = "403", description = "Writer permission required",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class)))
    })
    public ResponseEntity<CardResponse> create(@Valid @RequestBody CreateCardRequest request) {
        CardDetails details = createCardUseCase.create(
                new CardNumber(request.cardNumber()),
                new CardPassword(request.password()));
        URI location = URI.create("/api/v1/cards/" + details.id());
        return ResponseEntity.created(location).body(CardResponse.from(details));
    }

    @GetMapping("/{cardId}")
    @Operation(summary = "Get a card and its current balance by opaque identifier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Safe card representation"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid card identifier",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Card not found",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
            @ApiResponse(responseCode = "403", description = "Reader permission required",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ProblemResponse.class)))
    })
    public ResponseEntity<CardResponse> get(
            @Parameter(description = "Opaque public card identifier")
            @PathVariable String cardId) {
        return ResponseEntity.ok(CardResponse.from(getCardUseCase.get(CardId.from(cardId))));
    }
}
