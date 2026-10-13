package com.plataformacitas.identity.internal;

import com.plataformacitas.identity.internal.IdentityStore.OneTimeTokenRow;
import com.plataformacitas.shared.email.EmailSender;
import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import com.plataformacitas.shared.ratelimit.RateLimiter;
import com.plataformacitas.shared.ratelimit.RateLimiter.Policy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-101: recuperación de contraseña con token de un solo uso (solo su SHA-256 se guarda). */
@Service
public class PasswordResetService {

    static final String PURPOSE = "RESET_PASSWORD";
    static final String SUBJECT = "Restablece tu contraseña";

    private final IdentityStore store;
    private final TokenService tokens;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration ttl;
    private final String baseUrl;

    public PasswordResetService(IdentityStore store, TokenService tokens, RateLimiter rateLimiter,
            ApplicationEventPublisher events, Clock clock, @Value("${app.tokens.reset-password-ttl}") Duration ttl,
            @Value("${app.base-url}") String baseUrl) {
        this.store = store;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.clock = clock;
        this.ttl = ttl;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void requestReset(String email, String ip) {
        if (!rateLimiter.tryConsume(Policy.FORGOT_PASSWORD_IP, ip)) {
            throw new DomainException(Code.RATE_LIMITED, "Demasiadas solicitudes. Inténtalo más tarde.");
        }
        String normalized = IdentityStore.normalizeEmail(email);
        store.findByEmail(normalized)
                .filter(UserAccount::isActive)
                .filter(account -> rateLimiter.tryConsume(Policy.FORGOT_PASSWORD_EMAIL, normalized))
                .ifPresent(this::sendResetLink);
    }

    @Transactional
    public void reset(String token, String newPassword) {
        Instant now = clock.instant();
        OneTimeTokenRow row = (token == null ? java.util.Optional.<OneTimeTokenRow>empty()
                : store.lockUsableToken(PURPOSE, IdentityStore.sha256Hex(token), now))
                .orElseThrow(() -> new DomainException(Code.INVALID_TOKEN, "El enlace no es válido o ya venció."));
        store.changePassword(row.userId(), newPassword);
        store.markTokenUsed(row.id(), now);
        tokens.revokeAllForUser(row.userId());
    }

    private void sendResetLink(UserAccount account) {
        Instant now = clock.instant();
        UUID userId = account.getId();
        store.invalidateOpenTokens(userId, PURPOSE, now);
        String token = IdentityStore.newOpaqueToken();
        store.createOneTimeToken(userId, PURPOSE, IdentityStore.sha256Hex(token), null, now.plus(ttl));
        String link = baseUrl + "/restablecer-contrasena?token=" + token;
        String text = """
                Hola %s:

                Recibimos una solicitud para restablecer tu contraseña. Abre este enlace para elegir una nueva \
                (vence en 1 hora):

                %s

                Si no fuiste tú, ignora este correo: tu contraseña no cambia.
                """.formatted(account.getName(), link);
        String html = """
                <p>Hola %s:</p>
                <p>Recibimos una solicitud para restablecer tu contraseña. El enlace vence en 1 hora.</p>
                <p><a href="%s">Elegir una nueva contraseña</a></p>
                <p>Si no fuiste tú, ignora este correo: tu contraseña no cambia.</p>
                """.formatted(escape(account.getName()), link);
        events.publishEvent(new EmailSender.Message(account.getEmail(), SUBJECT, text, html));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
