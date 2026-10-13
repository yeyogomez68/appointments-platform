package com.plataformacitas.platform.internal;

import com.plataformacitas.shared.tenancy.BusinessScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Sede de un negocio. Hibernate rellena {@code business_id} desde {@code TenantContext} al persistir. */
@Entity
@Table(name = "location")
public class Location extends BusinessScopedEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String address;

    private String phone;

    @Column(name = "is_main", nullable = false)
    private boolean isMain;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Location() {}

    public static Location main(UUID id, String name, Instant now) {
        Location location = new Location();
        location.id = id;
        location.name = name;
        location.isMain = true;
        location.active = true;
        location.createdAt = now;
        location.updatedAt = now;
        return location;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isMain() {
        return isMain;
    }

    public boolean isActive() {
        return active;
    }
}
