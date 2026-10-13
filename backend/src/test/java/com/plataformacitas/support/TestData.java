package com.plataformacitas.support;

import com.plataformacitas.identity.internal.IdentityStore;
import com.plataformacitas.platform.Businesses;
import com.plataformacitas.shared.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Datos de prueba con nombres y correos únicos: la base del contenedor la comparten todas las clases. */
@Component
public class TestData {

    public record Seeded(UUID id, String name, String slug) {}

    public record SeededUser(UUID id, String email, String password) {}

    private final Businesses businesses;
    private final IdentityStore store;
    private final TransactionTemplate tx;

    public TestData(Businesses businesses, IdentityStore store, PlatformTransactionManager txManager) {
        this.businesses = businesses;
        this.store = store;
        this.tx = new TransactionTemplate(txManager);
    }

    public Seeded business(String name) {
        UUID id = UUID.randomUUID();
        String unique = name + " " + id.toString().substring(0, 8);
        var summary = TenantContext.run(id, null, () -> tx.execute(s ->
                businesses.provision(id, unique, "PELUQUERIA", "America/Bogota")));
        return new Seeded(id, summary.name(), summary.slug());
    }

    public SeededUser user(String email, String password, boolean verified) {
        var account = tx.execute(s -> store.createAccount(email, password, "Persona de prueba", verified));
        return new SeededUser(account.getId(), account.getEmail(), password);
    }

    /** Correo único con el prefijo dado. */
    public static String email(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    public void member(SeededUser user, Seeded business, String role) {
        TenantContext.run(business.id(), null, () -> tx.execute(s -> {
            store.addMembership(user.id(), business.id(), role);
            return null;
        }));
    }
}
