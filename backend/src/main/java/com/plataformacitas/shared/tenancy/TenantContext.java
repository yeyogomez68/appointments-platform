package com.plataformacitas.shared.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Negocio y cuenta activos del hilo actual. {@link #run} envuelve la transacción, nunca va dentro de una ya
 * abierta: {@code set_config} se aplica al iniciarla.
 */
public final class TenantContext {

    /** Identificador de tenant cuando no hay negocio activo (Hibernate no admite {@code null}). */
    public static final UUID NO_TENANT = new UUID(0, 0);

    private static final ThreadLocal<UUID> BUSINESS = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<UUID> businessId() {
        return Optional.ofNullable(BUSINESS.get());
    }

    public static Optional<UUID> userId() {
        return Optional.ofNullable(USER.get());
    }

    public static void set(UUID businessId, UUID userId) {
        setOrRemove(BUSINESS, businessId);
        setOrRemove(USER, userId);
    }

    public static void clear() {
        BUSINESS.remove();
        USER.remove();
    }

    /** Fija negocio y cuenta, ejecuta y restaura los valores anteriores. */
    public static <T> T run(UUID businessId, UUID userId, Supplier<T> work) {
        UUID previousBusiness = BUSINESS.get();
        UUID previousUser = USER.get();
        set(businessId, userId);
        try {
            return work.get();
        } finally {
            set(previousBusiness, previousUser);
        }
    }

    private static void setOrRemove(ThreadLocal<UUID> holder, UUID value) {
        if (value == null) {
            holder.remove();
        } else {
            holder.set(value);
        }
    }
}
