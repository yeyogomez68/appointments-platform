package com.plataformacitas.shared.money;

import com.plataformacitas.shared.error.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/** Importe inmutable en unidades menores de la moneda (centavos). Nunca {@code double}. */
public record Money(long amountMinor, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        Currency.getInstance(currency);
    }

    public static Money of(long minor, String currencyCode) {
        return new Money(minor, currencyCode);
    }

    /** Convierte un importe mayor a unidades menores con HALF_EVEN a los decimales por defecto de la moneda. */
    public static Money ofMajor(BigDecimal amount, String currencyCode) {
        int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        long minor = amount.setScale(digits, RoundingMode.HALF_EVEN).movePointRight(digits).longValueExact();
        return new Money(minor, currencyCode);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money times(int factor) {
        return new Money(Math.multiplyExact(amountMinor, factor), currency);
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new DomainException(DomainException.Code.CURRENCY_MISMATCH,
                    "No se pueden operar " + currency + " y " + other.currency);
        }
    }
}
