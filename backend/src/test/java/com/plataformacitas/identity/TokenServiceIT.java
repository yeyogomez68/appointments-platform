package com.plataformacitas.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.plataformacitas.identity.internal.IdentityStore;
import com.plataformacitas.identity.internal.TokenService;
import com.plataformacitas.identity.internal.TokenService.IssuedSession;
import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import com.plataformacitas.shared.security.JwtConfig;
import com.plataformacitas.support.IntegrationTest;
import com.plataformacitas.support.TestData;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TokenServiceIT extends IntegrationTest {

    @Autowired
    TokenService tokens;

    @Autowired
    TestData data;

    @Autowired
    JwtDecoder decoder;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    TestData.Seeded business;
    TestData.SeededUser user;

    @BeforeEach
    void setUp() {
        business = data.business("Tokens");
        user = data.user(TestData.email("tokens"), "clave-de-prueba-1", true);
        data.member(user, business, "OWNER");
    }

    @Test
    void accessTokenCarriesTenantClaims() {
        IssuedSession session = tokens.issueSession(user.id(), business.id());
        Jwt jwt = decoder.decode(session.accessToken());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
        assertThat(jwt.getClaimAsString("bid")).isEqualTo(business.id().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("OWNER");
        assertThat(jwt.getClaimAsString("typ")).isEqualTo("access");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(session.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void onlyTheHashOfTheRefreshTokenIsStored() {
        IssuedSession session = tokens.issueSession(user.id(), business.id());
        List<Map<String, Object>> rows = jdbc.sql("select token_hash, user_id, business_id from refresh_token where user_id = ?")
                .param(user.id()).query().listOfRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("token_hash", IdentityStore.sha256Hex(session.refreshToken()))
                .containsEntry("user_id", user.id())
                .containsEntry("business_id", business.id());
        assertThat(jdbc.sql("select count(*) from refresh_token where token_hash = ?")
                .param(session.refreshToken()).query(Long.class).single()).isZero();
    }

    @Test
    void rotationRevokesOldTokenAndKeepsFamily() {
        IssuedSession first = tokens.issueSession(user.id(), business.id());
        IssuedSession second = tokens.rotate(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.businessId()).isEqualTo(business.id());

        Map<String, Object> old = row(first.refreshToken());
        Map<String, Object> fresh = row(second.refreshToken());
        assertThat(old.get("revoked_at")).isNotNull();
        assertThat(old.get("replaced_by")).isEqualTo(fresh.get("id"));
        assertThat(fresh.get("revoked_at")).isNull();
        assertThat(fresh.get("family_id")).isEqualTo(old.get("family_id"));
        assertThat(fresh.get("business_id")).isEqualTo(business.id());
    }

    @Test
    void reuseOfRotatedTokenRevokesWholeFamily() {
        IssuedSession first = tokens.issueSession(user.id(), business.id());
        IssuedSession second = tokens.rotate(first.refreshToken());

        DomainException ex = assertThrows(DomainException.class, () -> tokens.rotate(first.refreshToken()));
        assertThat(ex.code()).isEqualTo(Code.INVALID_REFRESH_TOKEN);
        Object family = row(first.refreshToken()).get("family_id");
        assertThat(jdbc.sql("select count(*) from refresh_token where family_id = ? and revoked_at is null")
                .param(family).query(Long.class).single()).isZero();
        assertThat(assertThrows(DomainException.class, () -> tokens.rotate(second.refreshToken())).code())
                .isEqualTo(Code.INVALID_REFRESH_TOKEN);
    }

    @Test
    void protectedPathsAnswerProblemJson() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> anonymous = client.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/business")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        assertThat(code(anonymous)).isEqualTo("UNAUTHENTICATED");

        String preauth = tokens.issuePreAuth(user.id());
        HttpResponse<String> withPreauth = client.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/business"))
                .header("Authorization", "Bearer " + preauth).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(withPreauth.statusCode()).isEqualTo(403);
        assertThat(code(withPreauth)).isEqualTo("FORBIDDEN");
    }

    @Test
    void weakSecretIsRejected() {
        for (String bad : new String[] {"", "abc123", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde"}) {
            assertThatThrownBy(() -> JwtConfig.secretKey(bad))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET");
        }
        assertThat(JwtConfig.secretKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")).isNotNull();
    }

    private Map<String, Object> row(String rawToken) {
        return jdbc.sql("select id, family_id, business_id, revoked_at, replaced_by from refresh_token where token_hash = ?")
                .param(IdentityStore.sha256Hex(rawToken)).query().singleRow();
    }

    private String code(HttpResponse<String> response) {
        JsonNode body = json.readTree(response.body());
        return body.get("code").asString();
    }
}
