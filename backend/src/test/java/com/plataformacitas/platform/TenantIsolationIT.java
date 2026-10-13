package com.plataformacitas.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.plataformacitas.identity.internal.TokenService;
import com.plataformacitas.shared.tenancy.TenantContext;
import com.plataformacitas.support.IntegrationTest;
import com.plataformacitas.support.TestData;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Suite de aislamiento: crece sola con cada tabla nueva que tenga {@code business_id} y política RLS. */
class TenantIsolationIT extends IntegrationTest {

    private static final String SCOPED_TABLES = """
            SELECT c.relname
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind = 'r'
              AND n.nspname = 'public'
              AND EXISTS (SELECT 1 FROM pg_attribute a
                          WHERE a.attrelid = c.oid AND a.attname = 'business_id' AND NOT a.attisdropped)
              AND (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) >= 1
            ORDER BY c.relname
            """;

    @Autowired
    TestData data;

    @Autowired
    TokenService tokens;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    PlatformTransactionManager transactionManager;

    final HttpClient http = HttpClient.newHttpClient();
    TransactionTemplate tx;
    TestData.Seeded a;
    TestData.Seeded b;
    TestData.SeededUser ownerA;
    TestData.SeededUser receptionA;
    TestData.SeededUser ownerB;

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(transactionManager);
        a = data.business("Aislada A");
        b = data.business("Aislada B");
        ownerA = data.user(TestData.email("owner-a"), "clave-de-prueba-1", true);
        receptionA = data.user(TestData.email("recep-a"), "clave-de-prueba-1", true);
        ownerB = data.user(TestData.email("owner-b"), "clave-de-prueba-1", true);
        data.member(ownerA, a, "OWNER");
        data.member(receptionA, a, "RECEPTION");
        data.member(ownerB, b, "OWNER");
    }

    @Test
    void ownerReadsOnlyOwnBusiness() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/business", access(ownerA, a), null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("id").asString()).isEqualTo(a.id().toString());
        assertThat(body.get("name").asString()).isEqualTo(a.name());
        assertThat(body.get("slug").asString()).isEqualTo(a.slug());
        assertThat(body.get("timezone").asString()).isEqualTo("America/Bogota");
        assertThat(body.get("currency").asString()).isEqualTo("COP");
        assertThat(body.get("trialEndsAt").isNull()).isFalse();
        assertThat(body.get("labels").get("resource").asString()).isEqualTo("Empleado");
        assertThat(response.body()).doesNotContain(b.id().toString());
    }

    @Test
    void ownerRenamesOnlyOwnBusinessAndReceptionIsForbidden() throws Exception {
        String rename = update("A renombrada " + UUID.randomUUID().toString().substring(0, 6), "America/Bogota", "COP");
        HttpResponse<String> renamed = send("PUT", "/api/v1/business", access(ownerA, a), rename);
        assertThat(renamed.statusCode()).isEqualTo(200);
        assertThat(json.readTree(renamed.body()).get("name").asString()).startsWith("A renombrada");
        assertThat(jdbc.sql("select name from business where id = ?").param(b.id()).query(String.class).single())
                .isEqualTo(b.name());

        HttpResponse<String> forbidden = send("PUT", "/api/v1/business", access(receptionA, a), rename);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(json.readTree(forbidden.body()).get("code").asString()).isEqualTo("FORBIDDEN");
    }

    @Test
    void invalidZoneOrCurrencyIsValidationError() throws Exception {
        for (String body : List.of(update("Nombre válido", "Mars/Base", "COP"), update("Nombre válido", "America/Bogota", "XXXX"))) {
            HttpResponse<String> response = send("PUT", "/api/v1/business", access(ownerA, a), body);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Test
    void cannotSelectOrSwitchIntoForeignBusiness() throws Exception {
        String select = json.writeValueAsString(Map.of("businessId", b.id().toString()));
        HttpResponse<String> selected = send("POST", "/api/v1/auth/select-business", tokens.issuePreAuth(ownerA.id()), select);
        assertThat(selected.statusCode()).isEqualTo(403);
        assertThat(json.readTree(selected.body()).get("code").asString()).isEqualTo("FORBIDDEN");

        HttpResponse<String> switched = send("POST", "/api/v1/auth/switch-business", access(ownerA, a), select);
        assertThat(switched.statusCode()).isEqualTo(403);
        assertThat(json.readTree(switched.body()).get("code").asString()).isEqualTo("FORBIDDEN");
    }

    @Test
    void everyScopedTableHidesOtherBusiness() {
        List<String> tables = jdbc.sql(SCOPED_TABLES).query(String.class).list();
        assertThat(tables).contains("location", "business_settings", "membership");
        for (String table : tables) {
            TenantContext.run(a.id(), ownerA.id(), () -> tx.execute(s -> {
                assertThat(jdbc.sql("select count(*) from " + table + " where business_id = ?").param(b.id())
                        .query(Long.class).single()).as("select %s", table).isZero();
                assertThat(jdbc.sql("update " + table + " set business_id = business_id where business_id = ?")
                        .param(b.id()).update()).as("update %s", table).isZero();
                assertThat(jdbc.sql("delete from " + table + " where business_id = ?").param(b.id()).update())
                        .as("delete %s", table).isZero();
                return null;
            }));
        }
        DataAccessException ex = assertThrows(DataAccessException.class, () -> TenantContext.run(a.id(), ownerA.id(),
                () -> tx.execute(s -> jdbc.sql("insert into location (id, business_id, name, is_main, active) values (?, ?, 'x', false, true)")
                        .params(UUID.randomUUID(), b.id()).update())));
        assertThat(sqlState(ex)).isEqualTo("42501");
    }

    private String access(TestData.SeededUser user, TestData.Seeded business) {
        return tokens.issueSession(user.id(), business.id()).accessToken();
    }

    private String update(String name, String timezone, String currency) {
        return json.writeValueAsString(Map.of("name", name, "timezone", timezone, "currency", currency));
    }

    private HttpResponse<String> send(String method, String path, String bearer, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
