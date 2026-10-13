package com.plataformacitas.identity.internal;

import com.plataformacitas.identity.internal.IdentityStore.MembershipView;
import com.plataformacitas.identity.internal.TokenService.IssuedSession;
import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import com.plataformacitas.shared.ratelimit.RateLimiter;
import com.plataformacitas.shared.ratelimit.RateLimiter.Policy;
import com.plataformacitas.shared.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Login, selección y cambio de negocio. Los errores de credenciales no dan pistas. */
@Service
public class AuthService {

    public static final String BAD_CREDENTIALS = "Correo o contraseña incorrectos.";

    public enum LoginStatus { AUTHENTICATED, BUSINESS_SELECTION_REQUIRED }

    public record LoginResult(LoginStatus status, IssuedSession session, String preAuthToken,
            List<MembershipView> businesses) {}

    public record MeView(UUID userId, String email, String name, MembershipView business, List<MembershipView> businesses) {}

    private final IdentityStore store;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final RateLimiter rateLimiter;
    private final HttpServletRequest request;
    private volatile String dummyHash;

    public AuthService(IdentityStore store, TokenService tokens, PasswordEncoder passwordEncoder,
            PlatformTransactionManager txManager, RateLimiter rateLimiter, HttpServletRequest request) {
        this.store = store;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.tx = new TransactionTemplate(txManager);
        this.rateLimiter = rateLimiter;
        this.request = request;
    }

    public LoginResult login(String email, String password) {
        String ip = request.getRemoteAddr();
        if (rateLimiter.isExhausted(Policy.LOGIN_FAILURES, ip)) {
            throw new DomainException(Code.RATE_LIMITED, "Demasiados intentos. Espera un minuto e inténtalo de nuevo.");
        }
        Optional<UserAccount> found = tx.execute(s -> store.findByEmail(email));
        if (found.isEmpty()) {
            passwordEncoder.matches(password == null ? "" : password, dummyHash());
            throw badCredentials(ip);
        }
        UserAccount account = found.get();
        if (!store.passwordMatches(account, password) || !account.isActive()) {
            throw badCredentials(ip);
        }
        if (!account.isEmailVerified()) {
            throw new DomainException(Code.EMAIL_NOT_VERIFIED, "Confirma tu correo antes de iniciar sesión.");
        }
        List<MembershipView> memberships = membershipsOf(account.getId());
        if (memberships.isEmpty()) {
            throw new DomainException(Code.NO_ACTIVE_MEMBERSHIP, "Tu cuenta no tiene acceso a ningún negocio activo.");
        }
        if (memberships.size() == 1) {
            IssuedSession session = tokens.issueSession(account.getId(), memberships.getFirst().businessId());
            return new LoginResult(LoginStatus.AUTHENTICATED, session, null, null);
        }
        return new LoginResult(LoginStatus.BUSINESS_SELECTION_REQUIRED, null, tokens.issuePreAuth(account.getId()),
                memberships);
    }

    public IssuedSession selectBusiness(UUID userId, UUID businessId) {
        return tokens.issueSession(userId, businessId);
    }

    public IssuedSession switchBusiness(UUID userId, UUID businessId, String currentRawRefresh) {
        IssuedSession session = tokens.issueSession(userId, businessId);
        tokens.revokeFamily(currentRawRefresh);
        return session;
    }

    public MeView me(UUID userId, UUID businessId) {
        UserAccount account = tx.execute(s -> store.findById(userId))
                .orElseThrow(() -> new DomainException(Code.UNAUTHENTICATED, "La cuenta no existe."));
        List<MembershipView> memberships = membershipsOf(userId);
        MembershipView active = memberships.stream()
                .filter(m -> m.businessId().equals(businessId))
                .findFirst()
                .orElseThrow(() -> new DomainException(Code.FORBIDDEN, "No tienes acceso a este negocio."));
        return new MeView(account.getId(), account.getEmail(), account.getName(), active, memberships);
    }

    private List<MembershipView> membershipsOf(UUID userId) {
        return TenantContext.run(null, userId, () -> tx.execute(s -> store.membershipsOf(userId)));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("contraseña-ficticia-para-igualar-tiempos");
        }
        return dummyHash;
    }

    /** Cada intento fallido consume una ficha del límite por IP. */
    private DomainException badCredentials(String ip) {
        rateLimiter.tryConsume(Policy.LOGIN_FAILURES, ip);
        return new DomainException(Code.INVALID_CREDENTIALS, BAD_CREDENTIALS);
    }
}
