package com.plataformacitas.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.plataformacitas.platform.Businesses.BusinessSummary;
import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import com.plataformacitas.shared.tenancy.TenantContext;
import com.plataformacitas.support.IntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ProvisioningIT extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-10-12T15:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    Businesses businesses;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
    }

    private <T> T inBusiness(UUID id, java.util.function.Supplier<T> work) {
        return TenantContext.run(id, null, () -> tx.execute(s -> work.get()));
    }

    private BusinessSummary provision(UUID id, String name) {
        return inBusiness(id, () -> businesses.provision(id, name, "PELUQUERIA", "America/Bogota"));
    }

    @Test
    void sameNameGetsSuffixedSlug() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertThat(provision(first, "Peluquería Doña Ana").slug()).isEqualTo("peluqueria-dona-ana");
        assertThat(provision(second, "Peluquería Doña Ana").slug()).isEqualTo("peluqueria-dona-ana-2");

        Map<String, Object> row = jdbc.sql("select slug, timezone, currency, status from business where id = ?")
                .param(first).query().singleRow();
        assertThat(row).containsEntry("slug", "peluqueria-dona-ana")
                .containsEntry("timezone", "America/Bogota")
                .containsEntry("currency", "COP")
                .containsEntry("status", "ACTIVE");
    }

    @Test
    void createsOneMainLocation() {
        UUID id = UUID.randomUUID();
        provision(id, "Barbería " + id);
        var locations = inBusiness(id, () -> jdbc.sql("select name, is_main from location").query().listOfRows());
        assertThat(locations).hasSize(1);
        assertThat(locations.getFirst()).containsEntry("name", "Sede principal").containsEntry("is_main", true);
    }

    @Test
    void createsTrialSubscriptionFromClock() {
        UUID id = UUID.randomUUID();
        provision(id, "Salón " + id);
        Map<String, Object> sub = jdbc.sql("select plan_code, status, trial_ends_at from subscription where business_id = ?")
                .param(id).query().singleRow();
        assertThat(sub).containsEntry("plan_code", "PRO").containsEntry("status", "TRIAL");
        assertThat(((java.sql.Timestamp) sub.get("trial_ends_at")).toInstant())
                .isEqualTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void writesVerticalSettingsAndLabels() {
        UUID id = UUID.randomUUID();
        provision(id, "Estudio " + id);
        Map<String, Object> settings = inBusiness(id, () -> jdbc.sql(
                "select slot_step_min, labels ->> 'resource' as resource from business_settings").query().singleRow());
        assertThat(settings).containsEntry("slot_step_min", 15).containsEntry("resource", "Empleado");

        var details = inBusiness(id, () -> businesses.current());
        assertThat(details.labels()).containsEntry("resource", "Empleado");
        assertThat(details.trialEndsAt()).isEqualTo(Instant.parse("2026-11-11T15:00:00Z"));
    }

    @Test
    void unknownVerticalIsRejectedBeforeWriting() {
        UUID id = UUID.randomUUID();
        DomainException ex = assertThrows(DomainException.class,
                () -> inBusiness(id, () -> businesses.provision(id, "Taller " + id, "TALLER", "America/Bogota")));
        assertThat(ex.code()).isEqualTo(Code.VALIDATION_ERROR);
        assertThat(jdbc.sql("select count(*) from business where id = ?").param(id).query(Long.class).single()).isZero();
    }
}
