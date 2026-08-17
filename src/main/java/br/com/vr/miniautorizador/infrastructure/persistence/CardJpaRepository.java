package br.com.vr.miniautorizador.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CardJpaRepository extends JpaRepository<CardJpaEntity, Long> {

    boolean existsByCardNumber(String cardNumber);

    Optional<CardJpaEntity> findByPublicId(String publicId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO cards (public_id, card_number, password_hash, balance)
            VALUES (:publicId, :cardNumber, :passwordHash, :balance)
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("publicId") String publicId,
            @Param("cardNumber") String cardNumber,
            @Param("passwordHash") String passwordHash,
            @Param("balance") BigDecimal balance);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CardJpaEntity card
               set card.balance = card.balance - :amount
             where card.publicId = :publicId
               and card.balance >= :amount
            """)
    int debitIfBalanceIsAvailable(
            @Param("publicId") String publicId,
            @Param("amount") BigDecimal amount);
}
