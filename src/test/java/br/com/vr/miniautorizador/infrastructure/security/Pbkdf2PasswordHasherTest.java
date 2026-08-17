package br.com.vr.miniautorizador.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.model.PasswordHash;

class Pbkdf2PasswordHasherTest {

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(
            new PasswordSecurityProperties("test-pepper", 100_000, 16, 256),
            new SecureRandom());

    @Test
    void hashesAndMatchesPasswordWithoutPersistingPlainText() {
        CardPassword password = new CardPassword("1234");

        PasswordHash hash = hasher.hash(password);

        assertThat(hash.value()).startsWith("pbkdf2-sha256$100000$").doesNotContain(password.value());
        assertThat(hasher.matches(password, hash)).isTrue();
        assertThat(hasher.matches(new CardPassword("9999"), hash)).isFalse();
    }

    @Test
    void createsDifferentHashesBecauseEachPasswordReceivesItsOwnSalt() {
        CardPassword password = new CardPassword("1234");

        assertThat(hasher.hash(password)).isNotEqualTo(hasher.hash(password));
    }

    @Test
    void safelyRejectsMalformedHash() {
        assertThat(hasher.matches(new CardPassword("1234"), new PasswordHash("invalid"))).isFalse();
    }
}
