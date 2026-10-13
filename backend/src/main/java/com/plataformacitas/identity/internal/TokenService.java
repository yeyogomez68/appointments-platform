package com.plataformacitas.identity.internal;

import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import com.plataformacitas.shared.security.JwtConfig;
import com.plataformacitas.shared.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Emite tokens de acceso y preauth, y rota los refresh tokens opacos (solo su SHA-256 vive en la base). */
@Service
public class TokenService {

    public record IssuedSession(String accessToken, long expiresInSeconds, String refreshToken, UUID businessId,
            String businessName, String slug, String role) {}

    private record RefreshRow(UUID id, UUID userId, UUID businessId, UUID familyId, Instant expiresAt, Instant revokedAt) {}

    private sealed interface Rotation permits Rotation.Rejected, Rotation.Accepted {
        record Rejected() implements Rotation {}

        record Accepted(RefreshRow row, UUID newId) implements Rotation {}
    }

    private final JwtEncoder encoder;
    private final IdentityStore store;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration preauthTtl;
    private final Duration refreshTtl;

    public TokenService(JwtEncoder encoder, IdentityStore store, JdbcClient jdbc, PlatformTransactionManager txManager,
            Clock clock, @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.access-ttl}") Duration accessTtl,
            @Value("${app.jwt.preauth-ttl}") Duration preauthTtl, @Value("${app.jwt.refresh-ttl}") Duration refreshTtl) {
        this.encoder = encoder;
        this.store = store;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.preauthTtl = preauthTtl;
        this.refreshTtl = refreshTtl;
    }

    /** Sesión completa para una membresía activa: JWT de acceso + refresh opaco en una familia nueva. */
    public IssuedSession issueSession(UUID userId, UUID businessId) {
        return issue(userId, businessId, UUID.randomUUID(), UUID.randomUUID());
    }

    public String issuePreAuth(UUID userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("typ", JwtConfig.TYP_PREAUTH)
                .issuedAt(now)
                .expiresAt(now.plus(preauthTtl))
                .build();
        return encode(claims);
    }

    /** Rota el refresh token; la reutilización de uno ya rotado revoca toda su familia. */
    public IssuedSession rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalid();
        }
        String hash = IdentityStore.sha256Hex(rawRefreshToken);
        Instant now = clock.instant();
        Rotation rotation = tx.execute(s -> {
            Optional<RefreshRow> found = lockByHash(hash);
            if (found.isEmpty() || !found.get().expiresAt().isAfter(now)) {
                return new Rotation.Rejected();
            }
            RefreshRow row = found.get();
            if (row.revokedAt() != null) {
                revokeFamilyById(row.familyId(), now);
                return new Rotation.Rejected();
            }
            UUID newId = UUID.randomUUID();
            jdbc.sql("update refresh_token set revoked_at = ?, replaced_by = ? where id = ?")
                    .params(Timestamp.from(now), newId, row.id())
                    .update();
            return new Rotation.Accepted(row, newId);
        });
        if (!(rotation instanceof Rotation.Accepted accepted)) {
            throw invalid();
        }
        RefreshRow row = accepted.row();
        try {
            return issue(row.userId(), row.businessId(), row.familyId(), accepted.newId());
        } catch (DomainException e) {
            tx.executeWithoutResult(s -> revokeFamilyById(row.familyId(), clock.instant()));
            throw invalid();
        }
    }

    public void revokeFamily(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = IdentityStore.sha256Hex(rawRefreshToken);
        tx.executeWithoutResult(s -> lockByHash(hash).ifPresent(row -> revokeFamilyById(row.familyId(), clock.instant())));
    }

    public void revokeAllForUser(UUID userId) {
        tx.executeWithoutResult(s -> jdbc.sql("update refresh_token set revoked_at = ? where user_id = ? and revoked_at is null")
                .params(Timestamp.from(clock.instant()), userId)
                .update());
    }

    private IssuedSession issue(UUID userId, UUID businessId, UUID familyId, UUID refreshId) {
        return TenantContext.run(businessId, userId, () -> tx.execute(s -> {
            String role = store.activeRole(userId, businessId)
                    .orElseThrow(() -> new DomainException(Code.FORBIDDEN, "No tienes acceso a este negocio."));
            var business = jdbc.sql("select name, slug from business where id = ?")
                    .param(businessId)
                    .query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)})
                    .single();
            Instant now = clock.instant();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(userId.toString())
                    .claim("bid", businessId.toString())
                    .claim("role", role)
                    .claim("typ", JwtConfig.TYP_ACCESS)
                    .issuedAt(now)
                    .expiresAt(now.plus(accessTtl))
                    .build();
            String refresh = IdentityStore.newOpaqueToken();
            jdbc.sql("""
                    insert into refresh_token (id, user_id, business_id, family_id, token_hash, expires_at)
                    values (?, ?, ?, ?, ?, ?)""")
                    .params(refreshId, userId, businessId, familyId, IdentityStore.sha256Hex(refresh),
                            Timestamp.from(now.plus(refreshTtl)))
                    .update();
            return new IssuedSession(encode(claims), accessTtl.toSeconds(), refresh, businessId, business[0], business[1], role);
        }));
    }

    private Optional<RefreshRow> lockByHash(String hash) {
        return jdbc.sql("""
                select id, user_id, business_id, family_id, expires_at, revoked_at
                from refresh_token where token_hash = ? for update""")
                .param(hash)
                .query((rs, n) -> new RefreshRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getTimestamp(5).toInstant(),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()))
                .optional();
    }

    private void revokeFamilyById(UUID familyId, Instant at) {
        jdbc.sql("update refresh_token set revoked_at = ? where family_id = ? and revoked_at is null")
                .params(Timestamp.from(at), familyId)
                .update();
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static DomainException invalid() {
        return new DomainException(Code.INVALID_REFRESH_TOKEN, "La sesión expiró. Inicia sesión de nuevo.");
    }
}
