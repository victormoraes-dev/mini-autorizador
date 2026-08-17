package br.com.vr.miniautorizador.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.vr.miniautorizador.domain.model.Balance;
import br.com.vr.miniautorizador.domain.model.Card;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.PasswordHash;

class CardPersistenceMapperTest {

    private static final String PUBLIC_ID = "7c97bca5-3c85-4a2d-aab8-2d06112b56e4";

    private final CardPersistenceMapper mapper = new CardPersistenceMapper();

    @Test
    void mapsDomainToJpaWithoutLosingMoneyPrecision() {
        Card card = Card.restore(
                CardId.from(PUBLIC_ID),
                new CardNumber("00001234"),
                new PasswordHash("encoded-password"),
                new Balance(new BigDecimal("495.15")));

        CardJpaEntity entity = mapper.toEntity(card);

        assertThat(entity.publicId()).isEqualTo(PUBLIC_ID);
        assertThat(entity.cardNumber()).isEqualTo("00001234");
        assertThat(entity.passwordHash()).isEqualTo("encoded-password");
        assertThat(entity.balance()).isEqualByComparingTo("495.15");
    }

    @Test
    void rehydratesRichDomainFromJpaEntity() {
        CardJpaEntity entity = new CardJpaEntity(
                PUBLIC_ID,
                "6549873025634501",
                "encoded-password",
                new BigDecimal("490.00"));

        Card card = mapper.toDomain(entity);

        assertThat(card.id().value()).isEqualTo(UUID.fromString(PUBLIC_ID));
        assertThat(card.number().value()).isEqualTo("6549873025634501");
        assertThat(card.passwordHash()).isEqualTo(new PasswordHash("encoded-password"));
        assertThat(card.balance().value()).isEqualByComparingTo("490.00");
    }
}
