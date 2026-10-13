package com.plataformacitas.identity.internal;

import com.plataformacitas.identity.internal.IdentityStore.OneTimeTokenRow;
import com.plataformacitas.identity.internal.TokenService.IssuedSession;
import com.plataformacitas.platform.Businesses;
import com.plataformacitas.shared.captcha.CaptchaVerifier;
import com.plataformacitas.shared.email.EmailSender;
import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import com.plataformacitas.shared.ratelimit.RateLimiter;
import com.plataformacitas.shared.ratelimit.RateLimiter.Policy;
import com.plataformacitas.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HU-98: registro autoservicio con verificación de correo, y alta de negocios adicionales. */
@Service
public class SignupService {

    static final String PURPOSE = "VERIFY_EMAIL";
    static final String SUBJECT = "Confirma tu correo";

    public record SignupCommand(String businessName, String vertical, String timezone, String ownerName, String email,
            String password, boolean acceptTerms, boolean acceptDpa, String captchaToken) {}

    public record CreateBusinessCommand(String businessName, String vertical, String timezone, boolean acceptTerms,
            boolean acceptDpa) {}

    public record CreatedBusiness(UUID id, String name, String slug, String role) {}

    private final IdentityStore store;
    private final Businesses businesses;
    private final TokenService tokens;
    private final RateLimiter rateLimiter;
    private final CaptchaVerifier captcha;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration verifyTtl;
    private final String baseUrl;
    private final String termsVersion;
    private final String dpaVersion;

    public SignupService(IdentityStore store, Businesses businesses, TokenService tokens, RateLimiter rateLimiter,
            CaptchaVerifier captcha, ApplicationEventPublisher events, JsonMapper json, PlatformTransactionManager txManager,
            Clock clock, @Value("${app.tokens.verify-email-ttl}") Duration verifyTtl, @Value("${app.base-url}") String baseUrl,
            @Value("${app.legal.terms-version}") String termsVersion, @Value("${app.legal.dpa-version}") String dpaVersion) {
        this.store = store;
        this.businesses = businesses;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.captcha = captcha;
        this.events = events;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.verifyTtl = verifyTtl;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.termsVersion = termsVersion;
        this.dpaVersion = dpaVersion;
    }

    public void signup(SignupCommand c, String ip) {
        if (!rateLimiter.tryConsume(Policy.SIGNUP, ip)) {
            throw new DomainException(Code.RATE_LIMITED, "Demasiados registros desde esta red. Inténtalo más tarde.");
        }
        if (!captcha.verify(c.captchaToken(), ip)) {
            throw new DomainException(Code.CAPTCHA_FAILED, "No pudimos verificar que no eres un robot.");
        }
        String email = IdentityStore.normalizeEmail(c.email());
        if (tx.execute(s -> store.findByEmail(email)).isPresent()) {
            throw new DomainException(Code.ACCOUNT_EXISTS, "Ya existe una cuenta con ese correo.");
        }
        validZone(c.timezone());
        tx.executeWithoutResult(s -> {
            Instant now = clock.instant();
            UserAccount account = store.createAccount(email, c.password(), c.ownerName(), false);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("businessName", c.businessName().strip());
            payload.put("vertical", c.vertical());
            payload.put("timezone", c.timezone());
            payload.put("termsVersion", termsVersion);
            payload.put("dpaVersion", dpaVersion);
            payload.put("acceptedAt", now.toString());
            payload.put("ip", ip);
            String token = IdentityStore.newOpaqueToken();
            store.createOneTimeToken(account.getId(), PURPOSE, IdentityStore.sha256Hex(token),
                    json.writeValueAsString(payload), now.plus(verifyTtl));
            events.publishEvent(confirmationEmail(account, baseUrl + "/verificar-correo?token=" + token));
        });
    }

    /** Una sola transacción: verifica el correo y da de alta el negocio; la sesión se emite después del commit. */
    public IssuedSession verify(String token) {
        UUID businessId = UUID.randomUUID();
        UUID userId = TenantContext.run(businessId, null, () -> tx.execute(s -> {
            Instant now = clock.instant();
            OneTimeTokenRow row = (token == null || token.isBlank() ? java.util.Optional.<OneTimeTokenRow>empty()
                    : store.lockUsableToken(PURPOSE, IdentityStore.sha256Hex(token), now))
                    .orElseThrow(() -> new DomainException(Code.INVALID_TOKEN, "El enlace no es válido o ya venció."));
            JsonNode payload = json.readTree(row.payloadJson());
            store.markEmailVerified(row.userId(), now);
            businesses.provision(businessId, payload.get("businessName").asString(), payload.get("vertical").asString(),
                    payload.get("timezone").asString());
            store.addMembership(row.userId(), businessId, "OWNER");
            Instant acceptedAt = Instant.parse(payload.get("acceptedAt").asString());
            String ip = payload.hasNonNull("ip") ? payload.get("ip").asString() : null;
            businesses.recordLegalAcceptance(row.userId(), "TERMS", payload.get("termsVersion").asString(), acceptedAt, ip);
            businesses.recordLegalAcceptance(row.userId(), "DPA", payload.get("dpaVersion").asString(), acceptedAt, ip);
            store.markTokenUsed(row.id(), now);
            return row.userId();
        }));
        return tokens.issueSession(userId, businessId);
    }

    public CreatedBusiness createAnotherBusiness(UUID userId, CreateBusinessCommand c, String ip) {
        if (!c.acceptTerms() || !c.acceptDpa()) {
            throw new DomainException(Code.VALIDATION_ERROR, "Debes aceptar los términos y el acuerdo de encargo.");
        }
        UUID businessId = UUID.randomUUID();
        Businesses.BusinessSummary summary = TenantContext.run(businessId, userId, () -> tx.execute(s -> {
            Instant now = clock.instant();
            var created = businesses.provision(businessId, c.businessName(), c.vertical(), c.timezone());
            store.addMembership(userId, businessId, "OWNER");
            businesses.recordLegalAcceptance(userId, "TERMS", termsVersion, now, ip);
            businesses.recordLegalAcceptance(userId, "DPA", dpaVersion, now, ip);
            return created;
        }));
        return new CreatedBusiness(summary.id(), summary.name(), summary.slug(), "OWNER");
    }

    private EmailSender.Message confirmationEmail(UserAccount account, String link) {
        String text = """
                Hola %s:

                Gracias por registrarte. Confirma tu correo para activar tu negocio (el enlace vence en 24 horas):

                %s

                Si no creaste esta cuenta, ignora este correo.
                """.formatted(account.getName(), link);
        String html = """
                <p>Hola %s:</p>
                <p>Gracias por registrarte. Confirma tu correo para activar tu negocio. El enlace vence en 24 horas.</p>
                <p><a href="%s">Confirmar mi correo</a></p>
                <p>Si no creaste esta cuenta, ignora este correo.</p>
                """.formatted(account.getName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"), link);
        return new EmailSender.Message(account.getEmail(), SUBJECT, text, html);
    }

    private static void validZone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException e) {
            throw new DomainException(Code.VALIDATION_ERROR, "Zona horaria inválida: " + timezone);
        }
    }
}
