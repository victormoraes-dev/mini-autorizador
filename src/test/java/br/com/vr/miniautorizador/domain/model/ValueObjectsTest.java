package br.com.vr.miniautorizador.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ValueObjectsTest {

    @Test
    void createsAndParsesOpaqueCardIds() {
        CardId generated = CardId.generate();
        CardId parsed = CardId.from("7c97bca5-3c85-4a2d-aab8-2d06112b56e4");

        assertThat(generated.value()).isNotNull();
        assertThat(parsed.value()).isEqualTo(UUID.fromString("7c97bca5-3c85-4a2d-aab8-2d06112b56e4"));
        assertThat(parsed.toString()).isEqualTo("7c97bca5-3c85-4a2d-aab8-2d06112b56e4");
    }

    @Test
    void rejectsInvalidCardId() {
        assertThatThrownBy(() -> CardId.from("6549873025634501"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void treatsMoneyAndBalanceAsValuesWithTwoDecimalPlaces() {
        assertThat(Money.of("10")).isEqualTo(Money.of("10.00"));
        assertThat(new Balance(new BigDecimal("0"))).isEqualTo(new Balance(new BigDecimal("0.00")));
    }

    @Test
    void rejectsInvalidAmounts() {
        assertThatThrownBy(() -> Money.of("0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("-1.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("1.001")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidBalances() {
        assertThatThrownBy(() -> new Balance(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Balance(new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesLeadingZerosAndMasksCardNumber() {
        CardNumber number = new CardNumber("00001234");

        assertThat(number.value()).isEqualTo("00001234");
        assertThat(number.masked()).isEqualTo("****1234");
        assertThat(number.toString()).isEqualTo("****1234");
    }

    @Test
    void fullyMasksShortCardNumber() {
        assertThat(new CardNumber("1234").masked()).isEqualTo("****");
    }

    @Test
    void redactsPasswordValueObjects() {
        assertThat(new CardPassword("1234").toString()).isEqualTo("[REDACTED]");
        assertThat(new PasswordHash("encoded-value").toString()).isEqualTo("[REDACTED]");
    }

    @Test
    void rejectsBlankSensitiveValues() {
        assertThatThrownBy(() -> new CardNumber(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CardPassword(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PasswordHash(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
