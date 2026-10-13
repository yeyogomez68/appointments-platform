package com.plataformacitas.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Cuenta global: una persona, varios negocios (vía {@code membership}). */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "platform_role")
    private String platformRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {}

    UserAccount(UUID id, String email, String passwordHash, String name, Instant emailVerifiedAt, Instant now) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.status = "ACTIVE";
        this.emailVerifiedAt = emailVerifiedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void markEmailVerified(Instant at) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = at;
        }
        updatedAt = at;
    }

    void changePasswordHash(String hash, Instant at) {
        passwordHash = hash;
        updatedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getPlatformRole() {
        return platformRole;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }
}
