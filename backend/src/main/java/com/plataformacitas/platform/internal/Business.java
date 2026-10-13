package com.plataformacitas.platform.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Negocio (tabla global, sin RLS): lo leen el login y el selector antes de fijar el tenant. */
@Entity
@Table(name = "business")
public class Business {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(nullable = false)
    private String vertical;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Business() {}

    public Business(UUID id, String name, String slug, String timezone, String currency, String vertical, Instant now) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.timezone = timezone;
        this.currency = currency;
        this.vertical = vertical;
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String timezone, String currency, Instant now) {
        this.name = name;
        this.timezone = timezone;
        this.currency = currency;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCurrency() {
        return currency;
    }

    public String getVertical() {
        return vertical;
    }

    public String getStatus() {
        return status;
    }
}
