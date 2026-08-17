package br.com.vr.miniautorizador.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Balance(BigDecimal value) {

    public static final Balance INITIAL = new Balance(new BigDecimal("500.00"));

    public Balance {
        Objects.requireNonNull(value, "Balance is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Balance must not be negative");
        }
        if (value.scale() > Money.SCALE) {
            throw new IllegalArgumentException("Balance must have at most two decimal places");
        }
        value = value.setScale(Money.SCALE, RoundingMode.UNNECESSARY);
    }

    public boolean canCover(Money amount) {
        return value.compareTo(amount.value()) >= 0;
    }

    public Balance debit(Money amount) {
        if (!canCover(amount)) {
            throw new InsufficientBalanceException();
        }
        return new Balance(value.subtract(amount.value()));
    }
}
