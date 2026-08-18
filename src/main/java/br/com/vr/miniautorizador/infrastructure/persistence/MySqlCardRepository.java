package br.com.vr.miniautorizador.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.vr.miniautorizador.application.port.out.CardRepository;
import br.com.vr.miniautorizador.domain.model.Card;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.Money;

@Repository
class MySqlCardRepository implements CardRepository {

    private final CardJpaRepository repository;
    private final CardPersistenceMapper mapper;

    MySqlCardRepository(CardJpaRepository repository, CardPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public boolean existsByNumber(CardNumber cardNumber) {
        return repository.existsByCardNumber(cardNumber.value());
    }

    @Override
    @Transactional
    public boolean create(Card card) {
        return repository.insertIfAbsent(
                card.id().toString(),
                card.number().value(),
                card.passwordHash().value(),
                card.balance().value()) == 1;
    }

    @Override
    public Optional<Card> findById(CardId cardId) {
        return repository.findByPublicId(cardId.toString()).map(mapper::toDomain);
    }

    @Override
    public Optional<Card> findByNumber(CardNumber cardNumber) {
        return repository.findByCardNumber(cardNumber.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public boolean debitIfBalanceIsAvailable(CardId cardId, Money amount) {
        return repository.debitIfBalanceIsAvailable(cardId.toString(), amount.value()) == 1;
    }
}
