package com.plataformacitas.platform;

import com.plataformacitas.platform.internal.Business;
import com.plataformacitas.platform.internal.Location;
import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import com.plataformacitas.shared.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.text.Normalizer;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** API pública del módulo {@code platform}: alta y datos del negocio. */
@Service
public class Businesses {

    /** Puerto que implementa cada vertical con sus valores iniciales. */
    public interface VerticalSeed {
        String vertical();

        Defaults defaults();

        record Defaults(int slotStepMin, int bufferMin, boolean walkInsEnabled, long openingCashDefaultMinor,
                boolean tipsEnabled, String tipsRecipient, int maxDiscountPctReception, Map<String, String> labels) {}
    }

    public record BusinessSummary(UUID id, String name, String slug) {}

    public record BusinessDetails(UUID id, String name, String slug, String timezone, String currency, String vertical,
            String status, Instant trialEndsAt, Map<String, String> labels) {}

    static final String MAIN_LOCATION_NAME = "Sede principal";
    private static final int SLUG_MAX = 60;
    private static final TypeReference<Map<String, String>> LABELS_TYPE = new TypeReference<>() {};

    @PersistenceContext
    private EntityManager em;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final JsonMapper json;
    private final Map<String, VerticalSeed> seeds;
    private final Duration trialLength;

    public Businesses(JdbcClient jdbc, Clock clock, JsonMapper json, List<VerticalSeed> seeds,
            @Value("${app.trial-days}") int trialDays) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.json = json;
        this.seeds = seeds.stream().collect(Collectors.toMap(VerticalSeed::vertical, Function.identity()));
        this.trialLength = Duration.ofDays(trialDays);
    }

    /** Debe llamarse con una transacción abierta dentro de {@code TenantContext.run(businessId, …)}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public BusinessSummary provision(UUID businessId, String name, String vertical, String timezone) {
        VerticalSeed seed = seeds.get(vertical);
        if (seed == null) {
            throw new DomainException(Code.VALIDATION_ERROR, "Vertical desconocido: " + vertical);
        }
        String zone = validZone(timezone);
        String slug = uniqueSlug(name);
        Instant now = clock.instant();

        em.persist(new Business(businessId, name.strip(), slug, zone, "COP", vertical, now));
        em.flush();
        jdbc.sql("""
                insert into subscription (business_id, plan_code, status, trial_ends_at)
                values (?, 'PRO', 'TRIAL', ?)""")
                .params(businessId, java.sql.Timestamp.from(now.plus(trialLength)))
                .update();
        em.persist(Location.main(UUID.randomUUID(), MAIN_LOCATION_NAME, now));
        VerticalSeed.Defaults d = seed.defaults();
        jdbc.sql("""
                insert into business_settings (business_id, slot_step_min, buffer_min, walk_ins_enabled,
                    opening_cash_default_minor, tips_enabled, tips_recipient, max_discount_pct_reception, labels)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)""")
                .params(businessId, d.slotStepMin(), d.bufferMin(), d.walkInsEnabled(), d.openingCashDefaultMinor(),
                        d.tipsEnabled(), d.tipsRecipient(), d.maxDiscountPctReception(), json.writeValueAsString(d.labels()))
                .update();
        em.flush();
        return new BusinessSummary(businessId, name.strip(), slug);
    }

    @Transactional(readOnly = true)
    public List<BusinessSummary> summaries(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery("select b from Business b where b.id in :ids order by b.name", Business.class)
                .setParameter("ids", ids)
                .getResultList().stream()
                .map(b -> new BusinessSummary(b.getId(), b.getName(), b.getSlug()))
                .toList();
    }

    /** Negocio activo de {@code TenantContext}, con fin de prueba y etiquetas del vertical. */
    @Transactional(readOnly = true)
    public BusinessDetails current() {
        Business business = em.find(Business.class, activeBusiness());
        if (business == null) {
            throw new DomainException(Code.NOT_FOUND, "El negocio no existe.");
        }
        return details(business);
    }

    @Transactional
    public BusinessDetails update(String name, String timezone, String currency) {
        Business business = em.find(Business.class, activeBusiness());
        if (business == null) {
            throw new DomainException(Code.NOT_FOUND, "El negocio no existe.");
        }
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.length() < 2 || cleanName.length() > 120) {
            throw new DomainException(Code.VALIDATION_ERROR, "El nombre debe tener entre 2 y 120 caracteres.");
        }
        business.update(cleanName, validZone(timezone), validCurrency(currency), clock.instant());
        em.flush();
        return details(business);
    }

    private BusinessDetails details(Business business) {
        Instant trialEndsAt = jdbc.sql("select trial_ends_at from subscription where business_id = ?")
                .param(business.getId())
                .query((rs, n) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant())
                .optional().orElse(null);
        Map<String, String> labels = jdbc.sql("select labels::text from business_settings where business_id = ?")
                .param(business.getId())
                .query(String.class)
                .optional()
                .map(text -> json.readValue(text, LABELS_TYPE))
                .orElse(Map.of());
        return new BusinessDetails(business.getId(), business.getName(), business.getSlug(), business.getTimezone(),
                business.getCurrency(), business.getVertical(), business.getStatus(), trialEndsAt, labels);
    }

    private static UUID activeBusiness() {
        return TenantContext.businessId()
                .orElseThrow(() -> new DomainException(Code.UNAUTHENTICATED, "No hay un negocio activo."));
    }

    private static String validZone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException | NullPointerException e) {
            throw new DomainException(Code.VALIDATION_ERROR, "Zona horaria inválida: " + timezone);
        }
    }

    private static String validCurrency(String currency) {
        try {
            return Currency.getInstance(currency).getCurrencyCode();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException(Code.VALIDATION_ERROR, "Moneda inválida: " + currency);
        }
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        for (int n = 2; slugExists(candidate); n++) {
            String suffix = "-" + n;
            candidate = base.substring(0, Math.min(base.length(), SLUG_MAX - suffix.length())).replaceAll("-+$", "") + suffix;
        }
        return candidate;
    }

    private boolean slugExists(String slug) {
        return jdbc.sql("select count(*) from business where slug = ?").param(slug).query(Long.class).single() > 0;
    }

    static String slugify(String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > SLUG_MAX) {
            slug = slug.substring(0, SLUG_MAX).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            throw new DomainException(Code.VALIDATION_ERROR, "El nombre no produce un identificador válido.");
        }
        return slug;
    }
}
