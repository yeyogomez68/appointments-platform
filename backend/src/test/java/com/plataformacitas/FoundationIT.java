package com.plataformacitas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.plataformacitas.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class FoundationIT extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void runtimeRoleIsRestricted() {
        assertThat(jdbc.sql("select current_user").query(String.class).single()).isEqualTo("app_runtime");
        var flags = jdbc.sql("select rolsuper, rolbypassrls from pg_roles where rolname = 'app_runtime'")
                .query((rs, n) -> new boolean[] {rs.getBoolean(1), rs.getBoolean(2)}).single();
        assertThat(flags[0]).isFalse();
        assertThat(flags[1]).isFalse();
        assertThat(jdbc.sql("select tableowner from pg_tables where schemaname='public' and tablename='schema_info'")
                .query(String.class).single()).isEqualTo("app_owner");
        assertThat(jdbc.sql("select count(*) from pg_tables where schemaname='public' and tableowner='app_runtime'")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select value from schema_info where key='baseline'").query(String.class).single())
                .isEqualTo("plataforma-citas");
    }

    @Test
    void runtimeCannotCreateTables() throws SQLException {
        try (Connection c = connectAsRuntime(); Statement st = c.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () -> st.execute("create table x (id int)"));
            assertThat(ex.getSQLState()).isEqualTo("42501");
        }
    }

    @Test
    void healthIsPublicAndRestIsProtected() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        var health = client.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/actuator/health")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        var other = client.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/cualquier-cosa")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(other.statusCode()).isEqualTo(401);
    }
}
