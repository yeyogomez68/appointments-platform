package com.plataformacitas;

import static org.assertj.core.api.Assertions.assertThat;

import com.plataformacitas.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Exporta el contrato OpenAPI a {@code openapi.output} (por defecto {@code ../frontend/openapi/openapi.json}). */
@EnabledIfSystemProperty(named = "openapi.export", matches = "true")
class OpenApiExportIT extends IntegrationTest {

    @Test
    void exportContract() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/v3/api-docs")).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(200);

        String document = response.body().replace("\r\n", "\n");
        if (!document.endsWith("\n")) {
            document = document + "\n";
        }
        Path output = Path.of(System.getProperty("openapi.output", "../frontend/openapi/openapi.json"));
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, document, StandardCharsets.UTF_8);
    }
}
