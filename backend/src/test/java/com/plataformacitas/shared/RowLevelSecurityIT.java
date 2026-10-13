package com.plataformacitas.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.plataformacitas.shared.tenancy.TenantContext;
import com.plataformacitas.support.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class RowLevelSecurityIT extends IntegrationTest {

    /** Prueba de catálogo RLS (blueprint §4); {@code %s} es la condición sobre el esquema. */
    private static final String CATALOG_QUERY = """
            SELECT c.relname                                              AS table_name,
                   c.relrowsecurity                                       AS rls_enabled,
                   c.relforcerowsecurity                                  AS rls_forced,
                   (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS policies
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind = 'r'
              AND %s
              AND EXISTS (SELECT 1 FROM pg_attribute a
                          WHERE a.attrelid = c.oid AND a.attname = 'business_id' AND NOT a.attisdropped)
            ORDER BY c.relname
            """;
    private static final Set<String> GLOBAL_ALLOWLIST = Set.of("subscription", "refresh_token");

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate tx;
    UUID businessA;
    UUID businessB;
    UUID locationA;

    @BeforeEach
    void createTwoBusinesses() {
        tx = new TransactionTemplate(transactionManager);
        businessA = insertBusiness("a");
        businessB = insertBusiness("b");
        locationA = insertLocation(businessA);
        insertLocation(businessB);
    }

    @Test
    void everyBusinessTableInPublicIsProtected() throws SQLException {
        try (Connection c = connectAsRuntime()) {
            List<String> unprotected = unprotectedTables(c, "n.nspname = 'public'");
            unprotected.removeAll(GLOBAL_ALLOWLIST);
            assertThat(unprotected).isEmpty();
            assertThat(allTables(c, "n.nspname = 'public'")).contains("location", "business_settings");
        }
    }

    @Test
    void catalogCheckReportsUnprotectedTempTable() throws SQLException {
        try (Connection c = connectAsRuntime(); Statement st = c.createStatement()) {
            st.execute("create temp table probe_tbl (business_id uuid)");
            assertThat(unprotectedTables(c, "n.oid = pg_my_temp_schema()")).containsExactly("probe_tbl");
        }
    }

    @Test
    void withoutTenantRuntimeSeesNothingAndCannotInsert() throws SQLException {
        try (Connection c = connectAsRuntime(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("select count(*) from location")) {
                rs.next();
                assertThat(rs.getLong(1)).isZero();
            }
            try (PreparedStatement ps = c.prepareStatement("insert into location (id, business_id, name) values (?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, businessA);
                ps.setString(3, "Sede intrusa");
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertThat(ex.getSQLState()).isEqualTo("42501");
            }
        }
    }

    @Test
    void tenantTransactionSeesOnlyItsRowsAndSettingDoesNotLeak() {
        long visible = TenantContext.run(businessA, null, () -> tx.execute(s ->
                jdbc.sql("select count(*) from location").query(Long.class).single()));
        List<UUID> owners = TenantContext.run(businessA, null, () -> tx.execute(s ->
                jdbc.sql("select business_id from location").query(UUID.class).list()));
        assertThat(visible).isEqualTo(1);
        assertThat(owners).containsExactly(businessA);

        long afterwards = tx.execute(s -> jdbc.sql("select count(*) from location").query(Long.class).single());
        assertThat(afterwards).isZero();
    }

    private UUID insertBusiness(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into business (id, name, slug, vertical) values (?, ?, ?, 'PELUQUERIA')")
                .params(id, "Negocio " + prefix, prefix + "-" + id)
                .update();
        return id;
    }

    private UUID insertLocation(UUID businessId) {
        UUID id = UUID.randomUUID();
        TenantContext.run(businessId, null, () -> tx.execute(s ->
                jdbc.sql("insert into location (id, business_id, name, is_main) values (?, ?, 'Principal', true)")
                        .params(id, businessId)
                        .update()));
        return id;
    }

    private static List<String> unprotectedTables(Connection c, String schemaCondition) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(CATALOG_QUERY.formatted(schemaCondition))) {
            while (rs.next()) {
                boolean isProtected = rs.getBoolean("rls_enabled") && rs.getBoolean("rls_forced") && rs.getLong("policies") >= 1;
                if (!isProtected) {
                    result.add(rs.getString("table_name"));
                }
            }
        }
        return result;
    }

    private static List<String> allTables(Connection c, String schemaCondition) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(CATALOG_QUERY.formatted(schemaCondition))) {
            while (rs.next()) {
                result.add(rs.getString("table_name"));
            }
        }
        return result;
    }
}
