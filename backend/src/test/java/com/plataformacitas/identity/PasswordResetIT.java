package com.plataformacitas.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.plataformacitas.shared.email.EmailConfig.RecordingEmailSender;
import com.plataformacitas.shared.email.EmailSender;
import com.plataformacitas.shared.ratelimit.RateLimiter;
import com.plataformacitas.support.IntegrationTest;
import com.plataformacitas.support.TestData;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class PasswordResetIT extends IntegrationTest {

    static final String OLD_PASSWORD = "clave-anterior-1";
    static final String NEW_PASSWORD = "clave-nueva-2026";
    static final Pattern TOKEN = Pattern.compile("restablecer-contrasena\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    RecordingEmailSender mail;

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    TestData data;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    PlatformTransactionManager transactionManager;

    final HttpClient http = HttpClient.newHttpClient();
    TestData.SeededUser user;

    @BeforeEach
    void setUp() {
        mail.clear();
        rateLimiter.resetAll();
        TestData.Seeded business = data.business("Reset");
        user = data.user(TestData.email("reset"), OLD_PASSWORD, true);
        data.member(user, business, "OWNER");
    }

    @Test
    void emailIsSentOnlyAfterCommit() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            events.publishEvent(new EmailSender.Message("commit@example.com", "Commit", "texto", "<p>html</p>"));
            assertThat(mail.sent()).isEmpty();
        });
        tx.executeWithoutResult(s -> {
            events.publishEvent(new EmailSender.Message("rollback@example.com", "Rollback", "texto", "<p>html</p>"));
            s.setRollbackOnly();
        });
        assertThat(mail.sent()).extracting(EmailSender.Message::to).containsExactly("commit@example.com");
    }

    @Test
    void forgotAnswers202AndMailsOnlyRegisteredAddresses() throws Exception {
        assertThat(forgot(user.email()).statusCode()).isEqualTo(202);
        assertThat(forgot(TestData.email("desconocido")).statusCode()).isEqualTo(202);
        List<EmailSender.Message> sent = mail.sent();
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().to()).isEqualTo(user.email());
        assertThat(sent.getFirst().subject()).isEqualTo("Restablece tu contraseña");

        for (int i = 0; i < 3; i++) {
            assertThat(forgot(TestData.email("relleno")).statusCode()).isEqualTo(202);
        }
        HttpResponse<String> sixth = forgot(user.email());
        assertThat(sixth.statusCode()).isEqualTo(429);
        assertThat(json.readTree(sixth.body()).get("code").asString()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void resetChangesPasswordAndTokenIsSingleUse() throws Exception {
        forgot(user.email());
        String token = tokenFromMail();

        assertThat(reset(token, NEW_PASSWORD).statusCode()).isEqualTo(204);
        assertThat(login(user.email(), NEW_PASSWORD).statusCode()).isEqualTo(200);
        HttpResponse<String> old = login(user.email(), OLD_PASSWORD);
        assertThat(old.statusCode()).isEqualTo(401);
        assertThat(code(old)).isEqualTo("INVALID_CREDENTIALS");

        HttpResponse<String> again = reset(token, "otra-clave-nueva");
        assertThat(again.statusCode()).isEqualTo(400);
        assertThat(code(again)).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        forgot(user.email());
        String token = tokenFromMail();
        jdbc.sql("update one_time_token set expires_at = ? where user_id = ? and purpose = 'RESET_PASSWORD'")
                .params(Timestamp.from(Instant.now().minusSeconds(60)), user.id())
                .update();
        HttpResponse<String> response = reset(token, NEW_PASSWORD);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void resetRevokesExistingRefreshTokens() throws Exception {
        HttpResponse<String> logged = login(user.email(), OLD_PASSWORD);
        String setCookie = logged.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("rt=")).findFirst().orElseThrow();
        String rt = setCookie.substring(3, setCookie.indexOf(';'));

        forgot(user.email());
        assertThat(reset(tokenFromMail(), NEW_PASSWORD).statusCode()).isEqualTo(204);

        HttpResponse<String> refresh = http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/auth/refresh"))
                .header("Cookie", "rt=" + rt).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(refresh.statusCode()).isEqualTo(401);
        assertThat(code(refresh)).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    private String tokenFromMail() {
        EmailSender.Message message = mail.sent().getLast();
        Matcher m = TOKEN.matcher(message.text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private HttpResponse<String> forgot(String email) throws Exception {
        return post("/api/v1/auth/password/forgot", Map.of("email", email));
    }

    private HttpResponse<String> reset(String token, String password) throws Exception {
        return post("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", password));
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return post("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String code(HttpResponse<String> response) {
        return json.readTree(response.body()).get("code").asString();
    }
}
