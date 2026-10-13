package com.plataformacitas.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.plataformacitas.support.IntegrationTest;
import com.plataformacitas.support.TestData;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AuthFlowIT extends IntegrationTest {

    static final String PASSWORD = "clave-de-prueba-1";

    @Autowired
    TestData data;

    @Autowired
    JwtDecoder decoder;

    @Autowired
    JsonMapper json;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        TestData.SeededUser user = data.user(TestData.email("creds"), PASSWORD, true);
        HttpResponse<String> wrong = post("/api/v1/auth/login", login(user.email(), "otra-clave-123"), null, null);
        HttpResponse<String> unknown = post("/api/v1/auth/login", login(TestData.email("nadie"), PASSWORD), null, null);
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(body(wrong).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(body(unknown).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(body(wrong).get("detail").asString()).isEqualTo(body(unknown).get("detail").asString());
    }

    @Test
    void singleMembershipLogsInDirectlyWithCookie() throws Exception {
        TestData.Seeded business = data.business("Uno");
        TestData.SeededUser user = data.user(TestData.email("uno"), PASSWORD, true);
        data.member(user, business, "OWNER");

        HttpResponse<String> response = post("/api/v1/auth/login", login(user.email(), PASSWORD), null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("status").asString()).isEqualTo("AUTHENTICATED");
        assertThat(bid(body.get("session").get("accessToken").asString())).isEqualTo(business.id().toString());
        String setCookie = setCookie(response).orElseThrow();
        assertThat(setCookie).startsWith("rt=").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth");
    }

    @Test
    void twoMembershipsRequireSelection() throws Exception {
        TestData.Seeded a = data.business("A");
        TestData.Seeded b = data.business("B");
        TestData.SeededUser user = data.user(TestData.email("dos"), PASSWORD, true);
        data.member(user, a, "OWNER");
        data.member(user, b, "EMPLOYEE");

        JsonNode body = body(post("/api/v1/auth/login", login(user.email(), PASSWORD), null, null));
        assertThat(body.get("status").asString()).isEqualTo("BUSINESS_SELECTION_REQUIRED");
        assertThat(body.get("code").asString()).isEqualTo("BUSINESS_SELECTION_REQUIRED");
        assertThat(body.get("businesses").size()).isEqualTo(2);
        String preauth = body.get("preAuthToken").asString();

        HttpResponse<String> selected = post("/api/v1/auth/select-business", select(b.id()), preauth, null);
        assertThat(selected.statusCode()).isEqualTo(200);
        JsonNode session = body(selected);
        assertThat(bid(session.get("accessToken").asString())).isEqualTo(b.id().toString());
        assertThat(session.get("business").get("role").asString()).isEqualTo("EMPLOYEE");
    }

    @Test
    void switchBusinessChangesRoleAndMe() throws Exception {
        TestData.Seeded a = data.business("Dueña");
        TestData.Seeded b = data.business("Empleada");
        TestData.SeededUser user = data.user(TestData.email("cambia"), PASSWORD, true);
        data.member(user, a, "OWNER");
        data.member(user, b, "EMPLOYEE");
        String preauth = body(post("/api/v1/auth/login", login(user.email(), PASSWORD), null, null)).get("preAuthToken").asString();
        HttpResponse<String> inA = post("/api/v1/auth/select-business", select(a.id()), preauth, null);
        String accessA = body(inA).get("accessToken").asString();

        HttpResponse<String> switched = post("/api/v1/auth/switch-business", select(b.id()), accessA, cookieValue(inA));
        assertThat(switched.statusCode()).isEqualTo(200);
        JsonNode session = body(switched);
        assertThat(session.get("business").get("role").asString()).isEqualTo("EMPLOYEE");

        HttpResponse<String> me = get("/api/v1/auth/me", session.get("accessToken").asString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(body(me).get("business").get("id").asString()).isEqualTo(b.id().toString());
        assertThat(body(me).get("business").get("role").asString()).isEqualTo("EMPLOYEE");
        assertThat(body(me).get("businesses").size()).isEqualTo(2);
    }

    @Test
    void refreshRotatesAndLogoutKillsCookie() throws Exception {
        TestData.Seeded business = data.business("Refresh");
        TestData.SeededUser user = data.user(TestData.email("refresh"), PASSWORD, true);
        data.member(user, business, "RECEPTION");
        HttpResponse<String> logged = post("/api/v1/auth/login", login(user.email(), PASSWORD), null, null);
        String rt = cookieValue(logged);

        HttpResponse<String> refreshed = post("/api/v1/auth/refresh", "", null, rt);
        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(bid(body(refreshed).get("accessToken").asString())).isEqualTo(business.id().toString());
        String rotated = cookieValue(refreshed);
        assertThat(rotated).isNotBlank().isNotEqualTo(rt);

        HttpResponse<String> logout = post("/api/v1/auth/logout", "", null, rotated);
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(setCookie(logout).orElseThrow()).contains("Max-Age=0");

        HttpResponse<String> afterLogout = post("/api/v1/auth/refresh", "", null, rotated);
        assertThat(afterLogout.statusCode()).isEqualTo(401);
        assertThat(body(afterLogout).get("code").asString()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void unverifiedEmailIsRejectedAfterPassword() throws Exception {
        TestData.Seeded business = data.business("Sin verificar");
        TestData.SeededUser user = data.user(TestData.email("pendiente"), PASSWORD, false);
        data.member(user, business, "OWNER");
        HttpResponse<String> response = post("/api/v1/auth/login", login(user.email(), PASSWORD), null, null);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(body(response).get("code").asString()).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    private String login(String email, String password) {
        return json.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }

    private String select(UUID businessId) {
        return json.writeValueAsString(java.util.Map.of("businessId", businessId.toString()));
    }

    private HttpResponse<String> post(String path, String body, String bearer, String rtCookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        if (rtCookie != null) {
            request.header("Cookie", "rt=" + rtCookie);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(baseUrl() + path)).header("Authorization", "Bearer " + bearer).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private String bid(String accessToken) {
        return decoder.decode(accessToken).getClaimAsString("bid");
    }

    private static Optional<String> setCookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("rt=")).findFirst();
    }

    private static String cookieValue(HttpResponse<String> response) {
        String header = setCookie(response).orElseThrow();
        return header.substring("rt=".length(), header.indexOf(';'));
    }
}
