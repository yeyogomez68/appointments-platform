package com.plataformacitas.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de toda prueba de integración.
 *
 * <p>PostgreSQL 18 real (Testcontainers, un contenedor por JVM). La API se conecta como
 * {@code app_runtime} (sin BYPASSRLS y sin ser dueña de las tablas) y Flyway como {@code app_owner},
 * igual que en producción. Los roles los crea {@code src/test/resources/db/test-roles.sql}.
 *
 * <p>Las peticiones HTTP de las pruebas van contra el puerto real ({@link #baseUrl()}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    public static final String OWNER_USER = "app_owner";
    public static final String OWNER_PASSWORD = "owner_test";
    public static final String RUNTIME_USER = "app_runtime";
    public static final String RUNTIME_PASSWORD = "runtime_test";

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
                    .withDatabaseName("citas")
                    .withInitScript("db/test-roles.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> RUNTIME_USER);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> OWNER_USER);
        registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
    }

    @Value("${local.server.port}")
    protected int port;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Conexión JDBC directa como app_runtime, fuera de cualquier transacción de Spring. */
    protected static Connection connectAsRuntime() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME_USER, RUNTIME_PASSWORD);
    }
}
