package com.plataformacitas.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.plataformacitas.identity.internal.IdentityStore;
import com.plataformacitas.identity.internal.IdentityStore.MembershipView;
import com.plataformacitas.identity.internal.UserAccount;
import com.plataformacitas.platform.Businesses;
import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import com.plataformacitas.shared.tenancy.TenantContext;
import com.plataformacitas.support.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class IdentityPersistenceIT extends IntegrationTest {

    @Autowired
    IdentityStore store;

    @Autowired
    Businesses businesses;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate tx;
    UUID businessA;
    UUID businessB;
    UUID userId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        businessA = provision("Negocio A");
        businessB = provision("Negocio B");
        String suffix = UUID.randomUUID().toString();
        userId = tx.execute(s -> store.createAccount("persona-" + suffix + "@example.com", "clave-segura-123",
                "Persona", true)).getId();
        in(businessA, null, () -> {
            store.addMembership(userId, businessA, "OWNER");
            return null;
        });
        in(businessB, null, () -> {
            store.addMembership(userId, businessB, "EMPLOYEE");
            return null;
        });
    }

    @Test
    void migrationProtectsMembership() {
        var row = jdbc.sql("""
                select c.relrowsecurity, c.relforcerowsecurity,
                       exists (select 1 from pg_policy p where p.polrelid = c.oid and p.polname = 'membership_access')
                from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relname = 'membership'""")
                .query((rs, n) -> new boolean[] {rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3)})
                .single();
        assertThat(row).containsExactly(true, true, true);
        assertThat(jdbc.sql("""
                select count(*) from pg_tables where schemaname = 'public'
                and tablename in ('user_account', 'membership', 'refresh_token', 'one_time_token')""")
                .query(Long.class).single()).isEqualTo(4);
    }

    @Test
    void passwordIsStoredAsArgon2id() {
        String email = "argon-" + UUID.randomUUID() + "@example.com";
        UserAccount account = tx.execute(s -> store.createAccount(email, "mi-clave-secreta", "Argon", false));
        String hash = jdbc.sql("select password_hash from user_account where id = ?")
                .param(account.getId()).query(String.class).single();
        assertThat(hash).startsWith("$argon2id$").doesNotContain("mi-clave-secreta");
    }

    @Test
    void emailIsNormalizedAndUnique() {
        String local = "ana-" + UUID.randomUUID();
        tx.execute(s -> store.createAccount(local.toUpperCase().replace("ANA", "Ana") + "@Example.com", "clave-segura-123",
                "Ana", false));
        DomainException ex = assertThrows(DomainException.class,
                () -> tx.execute(s -> store.createAccount(local + "@example.com", "clave-segura-123", "Ana", false)));
        assertThat(ex.code()).isEqualTo(Code.ACCOUNT_EXISTS);
        assertThat(jdbc.sql("select count(*) from user_account where email = ?")
                .param(local + "@example.com").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void userSettingListsAllOwnMemberships() {
        List<MembershipView> memberships = in(null, userId, () -> store.membershipsOf(userId));
        assertThat(memberships).extracting(MembershipView::businessId).containsExactlyInAnyOrder(businessA, businessB);
        assertThat(memberships).extracting(MembershipView::role).containsExactlyInAnyOrder("OWNER", "EMPLOYEE");
    }

    @Test
    void businessSettingListsOnlyThatBusiness() {
        List<UUID> visible = in(businessA, null, () ->
                jdbc.sql("select business_id from membership").query(UUID.class).list());
        assertThat(visible).isNotEmpty().allMatch(businessA::equals);
    }

    @Test
    void withoutSettingsNothingIsVisibleAndCrossInsertFails() throws SQLException {
        long count = tx.execute(s -> jdbc.sql("select count(*) from membership").query(Long.class).single());
        assertThat(count).isZero();

        try (Connection c = connectAsRuntime()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("select set_config('app.business_id', '" + businessA + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into membership (id, user_id, business_id, role) values (?, ?, ?, 'RECEPTION')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, userId);
                ps.setObject(3, businessB);
                SQLException ex = assertThrows(SQLException.class, ps::executeUpdate);
                assertThat(ex.getSQLState()).isEqualTo("42501");
            }
            c.rollback();
        }
    }

    private UUID provision(String prefix) {
        UUID id = UUID.randomUUID();
        in(id, null, () -> businesses.provision(id, prefix + " " + id, "PELUQUERIA", "America/Bogota"));
        return id;
    }

    private <T> T in(UUID business, UUID user, Supplier<T> work) {
        return TenantContext.run(business, user, () -> tx.execute(s -> work.get()));
    }
}
