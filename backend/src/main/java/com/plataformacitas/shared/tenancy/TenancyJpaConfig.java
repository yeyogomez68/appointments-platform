package com.plataformacitas.shared.tenancy;

import jakarta.persistence.EntityManagerFactory;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Aislamiento en dos capas: Hibernate {@code @TenantId} con el negocio de {@link TenantContext}, y
 * {@code set_config('app.business_id', …, true)} al iniciar cada transacción para las políticas RLS.
 */
@Configuration(proxyBeanMethods = false)
public class TenancyJpaConfig {

    @Bean
    HibernatePropertiesCustomizer tenantResolverCustomizer() {
        return properties -> properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new BusinessTenantResolver());
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory);
    }

    static final class BusinessTenantResolver implements CurrentTenantIdentifierResolver<UUID> {
        @Override
        public UUID resolveCurrentTenantIdentifier() {
            return TenantContext.businessId().orElse(TenantContext.NO_TENANT);
        }

        @Override
        public boolean validateExistingCurrentSessions() {
            return false;
        }
    }

    static final class TenantAwareJpaTransactionManager extends JpaTransactionManager {
        TenantAwareJpaTransactionManager(EntityManagerFactory emf) {
            super(emf);
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            super.doBegin(transaction, definition);
            EntityManagerHolder holder =
                    (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
            holder.getEntityManager().unwrap(Session.class).doWork(connection -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "select set_config('app.business_id', ?, true), set_config('app.user_id', ?, true)")) {
                    ps.setString(1, TenantContext.businessId().map(UUID::toString).orElse(""));
                    ps.setString(2, TenantContext.userId().map(UUID::toString).orElse(""));
                    ps.execute();
                }
            });
        }
    }
}
