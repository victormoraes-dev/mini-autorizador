package br.com.vr.miniautorizador.infrastructure.persistence;

import org.springframework.stereotype.Component;

import br.com.vr.miniautorizador.domain.model.Balance;
import br.com.vr.miniautorizador.domain.model.Card;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.PasswordHash;

@Component
class CardPersistenceMapper {

    CardJpaEntity toEntity(Card card) {
        return new CardJpaEntity(
                card.id().toString(),
                card.number().value(),
                card.passwordHash().value(),
                card.balance().value());
    }

    Card toDomain(CardJpaEntity entity) {
        return Card.restore(
                CardId.from(entity.publicId()),
                new CardNumber(entity.cardNumber()),
                new PasswordHash(entity.passwordHash()),
                new Balance(entity.balance()));
    }
}
