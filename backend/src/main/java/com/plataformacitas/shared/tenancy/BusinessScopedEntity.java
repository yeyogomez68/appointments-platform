package com.plataformacitas.shared.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Base de toda entidad JPA de un negocio: Hibernate filtra y asigna {@code business_id} desde {@link TenantContext}. */
@MappedSuperclass
public abstract class BusinessScopedEntity {

    @TenantId
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    public UUID getBusinessId() {
        return businessId;
    }
}
