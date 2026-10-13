package com.plataformacitas.identity.internal;

import com.plataformacitas.platform.Businesses;
import com.plataformacitas.shared.tenancy.TenantContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Datos de demostración (perfil {@code dev} y staging con {@code APP_SEED_DEMO=true}). Idempotente. */
@Component
@Conditional(DemoDataSeeder.SeedEnabled.class)
public class DemoDataSeeder implements ApplicationRunner {

    /**
     * Siempre en el perfil {@code dev}; fuera de él, con {@code app.seed.demo=true}. No basta la propiedad: el
     * {@code .env} local trae {@code APP_SEED_DEMO=false} y, exportado como variable de entorno, pesaría más que el
     * {@code app.seed.demo: true} del perfil {@code dev}.
     */
    static final class SeedEnabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment env = context.getEnvironment();
            return env.acceptsProfiles(Profiles.of("dev")) || "true".equalsIgnoreCase(env.getProperty("app.seed.demo"));
        }
    }

    static final String OWNER_EMAIL = "owner@demo.local";
    static final String RECEPTION_EMAIL = "recepcion@demo.local";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final Businesses businesses;
    private final IdentityStore store;
    private final TransactionTemplate tx;
    private final String password;

    public DemoDataSeeder(Businesses businesses, IdentityStore store, PlatformTransactionManager txManager,
            @Value("${app.seed.demo-password:}") String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("DEV_SEED_PASSWORD es obligatorio cuando la siembra de demostración está activa");
        }
        this.businesses = businesses;
        this.store = store;
        this.tx = new TransactionTemplate(txManager);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tx.execute(s -> store.findByEmail(OWNER_EMAIL)).isPresent()) {
            return;
        }
        UUID north = provision("Peluquería Demo Norte");
        UUID south = provision("Peluquería Demo Sur");
        UserAccount owner = tx.execute(s -> store.createAccount(OWNER_EMAIL, password, "Dueña Demo", true));
        UserAccount reception = tx.execute(s -> store.createAccount(RECEPTION_EMAIL, password, "Recepción Demo", true));
        member(owner.getId(), north, "OWNER");
        member(owner.getId(), south, "OWNER");
        member(reception.getId(), north, "RECEPTION");
        log.info("Datos de demostración creados: {} y {}", OWNER_EMAIL, RECEPTION_EMAIL);
    }

    private UUID provision(String name) {
        UUID id = UUID.randomUUID();
        TenantContext.run(id, null, () -> tx.execute(s -> businesses.provision(id, name, "PELUQUERIA", "America/Bogota")));
        return id;
    }

    private void member(UUID userId, UUID businessId, String role) {
        TenantContext.run(businessId, null, () -> tx.execute(s -> {
            store.addMembership(userId, businessId, role);
            return null;
        }));
    }
}
