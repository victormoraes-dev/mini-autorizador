package br.com.vr.miniautorizador.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal value) implements Comparable<Money> {

    public static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(value, "Amount is required");

        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (value.scale() > SCALE) {
            throw new IllegalArgumentException("Amount must have at most two decimal places");
        }
        value = value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Money of(String value) {
        return new Money(new BigDecimal(value));
    }

    @Override
    public int compareTo(Money other) {
        return value.compareTo(other.value);
    }
}
