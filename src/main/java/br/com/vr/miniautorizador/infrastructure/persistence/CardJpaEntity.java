package br.com.vr.miniautorizador.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cards")
class CardJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "card_number", nullable = false, unique = true, length = 32)
    private String cardNumber;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected CardJpaEntity() {
    }

    CardJpaEntity(String publicId, String cardNumber, String passwordHash, BigDecimal balance) {
        this.publicId = publicId;
        this.cardNumber = cardNumber;
        this.passwordHash = passwordHash;
        this.balance = balance;
    }

    Long id() {
        return id;
    }

    String publicId() {
        return publicId;
    }

    String cardNumber() {
        return cardNumber;
    }

    String passwordHash() {
        return passwordHash;
    }

    BigDecimal balance() {
        return balance;
    }
}
