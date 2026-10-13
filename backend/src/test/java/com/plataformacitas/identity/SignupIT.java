package com.plataformacitas.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.plataformacitas.platform.Businesses;
import com.plataformacitas.shared.captcha.CaptchaVerifier;
import com.plataformacitas.shared.email.EmailConfig.RecordingEmailSender;
import com.plataformacitas.shared.email.EmailSender;
import com.plataformacitas.shared.ratelimit.RateLimiter;
import com.plataformacitas.shared.tenancy.TenantContext;
import com.plataformacitas.support.IntegrationTest;
import com.plataformacitas.support.TestData;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SignupIT extends IntegrationTest {

    static final Pattern TOKEN = Pattern.compile("/verificar-correo\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    RecordingEmailSender mail;

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    JwtDecoder decoder;

    @Autowired
    TestData data;

    @Autowired
    PlatformTransactionManager transactionManager;

    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        mail.clear();
        rateLimiter.resetAll();
    }

    @Test
    void signupCreatesUnverifiedAccountAndSendsConfirmation() throws Exception {
        String email = TestData.email("registro");
        HttpResponse<String> response = post("/api/v1/signup", signup(email, CaptchaVerifier.DUMMY_TOKEN), null);
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(body(response).get("status").asString()).isEqualTo("VERIFICATION_SENT");
        assertThat(jdbc.sql("select email_verified_at is null from user_account where email = ?")
                .param(email).query(Boolean.class).single()).isTrue();
        List<EmailSender.Message> sent = mail.sent();
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().to()).isEqualTo(email);
        assertThat(sent.getFirst().subject()).isEqualTo("Confirma tu correo");
        assertThat(sent.getFirst().text()).contains("/verificar-correo?token=");
    }

    @Test
    void duplicateEmailCaptchaAndRateLimitAreRejected() throws Exception {
        String email = TestData.email("duplicado");
        assertThat(post("/api/v1/signup", signup(email, CaptchaVerifier.DUMMY_TOKEN), null).statusCode()).isEqualTo(202);
        HttpResponse<String> duplicate = post("/api/v1/signup", signup(email.toUpperCase(), CaptchaVerifier.DUMMY_TOKEN), null);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(code(duplicate)).isEqualTo("ACCOUNT_EXISTS");
        assertThat(jdbc.sql("select count(*) from user_account where email = ?").param(email).query(Long.class).single())
                .isEqualTo(1);

        HttpResponse<String> badCaptcha = post("/api/v1/signup", signup(TestData.email("robot"), "token-invalido"), null);
        assertThat(badCaptcha.statusCode()).isEqualTo(400);
        assertThat(code(badCaptcha)).isEqualTo("CAPTCHA_FAILED");

        for (int i = 0; i < 2; i++) {
            assertThat(post("/api/v1/signup", signup(TestData.email("lote"), CaptchaVerifier.DUMMY_TOKEN), null).statusCode())
                    .isEqualTo(202);
        }
        HttpResponse<String> sixth = post("/api/v1/signup", signup(TestData.email("sexto"), CaptchaVerifier.DUMMY_TOKEN), null);
        assertThat(sixth.statusCode()).isEqualTo(429);
        assertThat(code(sixth)).isEqualTo("RATE_LIMITED");
    }

    @Test
    void verifyCreatesBusinessInOneGo() throws Exception {
        String email = TestData.email("verifica");
        post("/api/v1/signup", signup(email, CaptchaVerifier.DUMMY_TOKEN), null);
        String token = tokenFromMail();

        HttpResponse<String> verified = post("/api/v1/signup/verify", json.writeValueAsString(Map.of("token", token)), null);
        assertThat(verified.statusCode()).isEqualTo(200);
        assertThat(verified.headers().allValues("Set-Cookie")).anyMatch(c -> c.startsWith("rt="));
        JsonNode session = body(verified);
        UUID businessId = UUID.fromString(session.get("business").get("id").asString());
        assertThat(session.get("business").get("role").asString()).isEqualTo("OWNER");
        assertThat(decoder.decode(session.get("accessToken").asString()).getClaimAsString("bid")).isEqualTo(businessId.toString());

        UUID userId = jdbc.sql("select id from user_account where email = ? and email_verified_at is not null")
                .param(email).query(UUID.class).single();
        Map<String, Object> subscription = jdbc.sql("select status, trial_ends_at, created_at from subscription where business_id = ?")
                .param(businessId).query().singleRow();
        assertThat(subscription.get("status")).isEqualTo("TRIAL");
        assertThat(Duration.between(((Timestamp) subscription.get("created_at")).toInstant(),
                ((Timestamp) subscription.get("trial_ends_at")).toInstant()).toDays()).isEqualTo(30);

        Map<String, Object> scoped = inBusiness(businessId, () -> Map.of(
                "locations", jdbc.sql("select count(*) from location where is_main").query(Long.class).single(),
                "settings", jdbc.sql("select count(*) from business_settings").query(Long.class).single(),
                "owner", jdbc.sql("select role from membership where user_id = ?").param(userId).query(String.class).single(),
                "legal", jdbc.sql("select document || ':' || version from legal_acceptance order by document")
                        .query(String.class).list()));
        assertThat(scoped).containsEntry("locations", 1L).containsEntry("settings", 1L).containsEntry("owner", "OWNER");
        assertThat(scoped.get("legal")).isEqualTo(List.of("DPA:borrador-2026-10", "TERMS:borrador-2026-10"));

        HttpResponse<String> again = post("/api/v1/signup/verify", json.writeValueAsString(Map.of("token", token)), null);
        assertThat(again.statusCode()).isEqualTo(400);
        assertThat(code(again)).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void authenticatedAccountCreatesAnotherBusiness() throws Exception {
        TestData.Seeded first = data.business("Primero");
        TestData.SeededUser user = data.user(TestData.email("otro"), "clave-de-prueba-1", true);
        data.member(user, first, "OWNER");
        String login = json.writeValueAsString(Map.of("email", user.email(), "password", user.password()));
        String access = body(post("/api/v1/auth/login", login, null)).get("session").get("accessToken").asString();

        String request = json.writeValueAsString(Map.of("businessName", "Segundo " + UUID.randomUUID().toString().substring(0, 6),
                "vertical", "PELUQUERIA", "timezone", "America/Bogota", "acceptTerms", true, "acceptDpa", true));
        HttpResponse<String> created = post("/api/v1/businesses", request, access);
        assertThat(created.statusCode()).isEqualTo(201);
        String newId = body(created).get("id").asString();
        assertThat(body(created).get("role").asString()).isEqualTo("OWNER");

        HttpResponse<String> me = http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/auth/me"))
                .header("Authorization", "Bearer " + access).build(), HttpResponse.BodyHandlers.ofString());
        List<String> ids = new java.util.ArrayList<>();
        body(me).get("businesses").forEach(b -> ids.add(b.get("id").asString()));
        assertThat(ids).contains(newId, first.id().toString());
    }

    @Test
    void publicConfigExposesVersions() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/public/config")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response).get("termsVersion").asString()).isEqualTo("borrador-2026-10");
        assertThat(body(response).get("captchaSiteKey").isString()).isTrue();
    }

    @Nested
    class WhenVerticalSeedFails {

        @MockitoBean
        Businesses.VerticalSeed failingSeed;

        @BeforeEach
        void failSeed() {
            when(failingSeed.vertical()).thenReturn("PELUQUERIA");
            when(failingSeed.defaults()).thenThrow(new IllegalStateException("semilla rota"));
        }

        @Test
        void verificationRollsBackEverything() throws Exception {
            String email = TestData.email("reversion");
            post("/api/v1/signup", signup(email, CaptchaVerifier.DUMMY_TOKEN), null);
            String token = tokenFromMail();
            long businessesBefore = jdbc.sql("select count(*) from business").query(Long.class).single();

            HttpResponse<String> response = post("/api/v1/signup/verify", json.writeValueAsString(Map.of("token", token)), null);
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(code(response)).isEqualTo("INTERNAL_ERROR");

            assertThat(jdbc.sql("select count(*) from business").query(Long.class).single()).isEqualTo(businessesBefore);
            UUID userId = jdbc.sql("select id from user_account where email = ? and email_verified_at is null")
                    .param(email).query(UUID.class).single();
            assertThat(inBusiness(null, userId, () -> jdbc.sql("select count(*) from membership").query(Long.class).single()))
                    .isZero();
            assertThat(jdbc.sql("select count(*) from one_time_token where user_id = ? and used_at is null")
                    .param(userId).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("select count(*) from legal_acceptance").query(Long.class).single()).isZero();
        }
    }

    private String signup(String email, String captchaToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("businessName", "Salón " + UUID.randomUUID().toString().substring(0, 8));
        body.put("vertical", "PELUQUERIA");
        body.put("timezone", "America/Bogota");
        body.put("ownerName", "Ana Dueña");
        body.put("email", email);
        body.put("password", "clave-segura-2026");
        body.put("acceptTerms", true);
        body.put("acceptDpa", true);
        body.put("captchaToken", captchaToken);
        return json.writeValueAsString(body);
    }

    private String tokenFromMail() {
        Matcher m = TOKEN.matcher(mail.sent().getLast().text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private <T> T inBusiness(UUID businessId, java.util.function.Supplier<T> work) {
        return inBusiness(businessId, null, work);
    }

    private <T> T inBusiness(UUID businessId, UUID userId, java.util.function.Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return TenantContext.run(businessId, userId, () -> tx.execute(s -> work.get()));
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private String code(HttpResponse<String> response) {
        return body(response).get("code").asString();
    }
}
