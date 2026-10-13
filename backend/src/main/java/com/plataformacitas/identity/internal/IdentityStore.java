package com.plataformacitas.identity.internal;

import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

/**
 * Persistencia del módulo {@code identity}. Las operaciones de membresías deben correr dentro de
 * {@code TenantContext.run(...)} + transacción: la política de {@code membership} exige
 * {@code app.business_id} o {@code app.user_id}.
 */
@Repository
public class IdentityStore {

    public record MembershipView(UUID businessId, String businessName, String slug, String role) {}

    public record OneTimeTokenRow(UUID id, UUID userId, String purpose, String payloadJson, Instant expiresAt) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    @PersistenceContext
    private EntityManager em;

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public IdentityStore(JdbcClient jdbc, PasswordEncoder passwordEncoder, Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public UserAccount createAccount(String email, String rawPassword, String name, boolean verified) {
        String normalized = normalizeEmail(email);
        if (findByEmail(normalized).isPresent()) {
            throw accountExists();
        }
        Instant now = clock.instant();
        UserAccount account = new UserAccount(UUID.randomUUID(), normalized, passwordEncoder.encode(rawPassword),
                name.strip(), verified ? now : null, now);
        try {
            em.persist(account);
            em.flush();
        } catch (DataIntegrityViolationException | jakarta.persistence.PersistenceException e) {
            if (isUniqueViolation(e)) {
                throw accountExists();
            }
            throw e;
        }
        return account;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return em.createQuery("select u from UserAccount u where u.email = :email", UserAccount.class)
                .setParameter("email", normalizeEmail(email))
                .getResultStream().findFirst();
    }

    public Optional<UserAccount> findById(UUID userId) {
        return Optional.ofNullable(em.find(UserAccount.class, userId));
    }

    public void markEmailVerified(UUID userId, Instant at) {
        require(userId).markEmailVerified(at);
        em.flush();
    }

    public void changePassword(UUID userId, String rawPassword) {
        require(userId).changePasswordHash(passwordEncoder.encode(rawPassword), clock.instant());
        em.flush();
    }

    public boolean passwordMatches(UserAccount account, String rawPassword) {
        return rawPassword != null && passwordEncoder.matches(rawPassword, account.getPasswordHash());
    }

    // --- Membresías ---

    public void addMembership(UUID userId, UUID businessId, String role) {
        jdbc.sql("insert into membership (id, user_id, business_id, role) values (?, ?, ?, ?)")
                .params(UUID.randomUUID(), userId, businessId, role)
                .update();
    }

    public List<MembershipView> membershipsOf(UUID userId) {
        return jdbc.sql("""
                select m.business_id, b.name, b.slug, m.role
                from membership m
                join business b on b.id = m.business_id
                where m.user_id = ? and m.active and b.status = 'ACTIVE'
                order by b.name""")
                .param(userId)
                .query((rs, n) -> new MembershipView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4)))
                .list();
    }

    public Optional<String> activeRole(UUID userId, UUID businessId) {
        return jdbc.sql("""
                select m.role from membership m join business b on b.id = m.business_id
                where m.user_id = ? and m.business_id = ? and m.active and b.status = 'ACTIVE'""")
                .params(userId, businessId)
                .query(String.class)
                .optional();
    }

    // --- Tokens de un solo uso ---

    public UUID createOneTimeToken(UUID userId, String purpose, String tokenHash, String payloadJson, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into one_time_token (id, user_id, purpose, token_hash, payload, expires_at)
                values (?, ?, ?, ?, ?::jsonb, ?)""")
                .params(id, userId, purpose, tokenHash, payloadJson == null ? "{}" : payloadJson, Timestamp.from(expiresAt))
                .update();
        return id;
    }

    public Optional<OneTimeTokenRow> lockUsableToken(String purpose, String tokenHash, Instant now) {
        return jdbc.sql("""
                select id, user_id, purpose, payload::text, expires_at from one_time_token
                where purpose = ? and token_hash = ? and used_at is null and expires_at > ?
                for update""")
                .params(purpose, tokenHash, Timestamp.from(now))
                .query((rs, n) -> new OneTimeTokenRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()))
                .optional();
    }

    public void markTokenUsed(UUID id, Instant at) {
        jdbc.sql("update one_time_token set used_at = ? where id = ?").params(Timestamp.from(at), id).update();
    }

    public void invalidateOpenTokens(UUID userId, String purpose, Instant at) {
        jdbc.sql("update one_time_token set used_at = ? where user_id = ? and purpose = ? and used_at is null")
                .params(Timestamp.from(at), userId, purpose)
                .update();
    }

    // --- Utilidades ---

    /** 32 bytes aleatorios en base64url sin relleno. */
    public static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private UserAccount require(UUID userId) {
        UserAccount account = em.find(UserAccount.class, userId);
        if (account == null) {
            throw new DomainException(Code.NOT_FOUND, "La cuenta no existe.");
        }
        return account;
    }

    private static DomainException accountExists() {
        return new DomainException(Code.ACCOUNT_EXISTS, "Ya existe una cuenta con ese correo.");
    }

    private static boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
