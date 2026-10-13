package com.plataformacitas.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.plataformacitas.shared.captcha.CaptchaVerifier;
import com.plataformacitas.shared.captcha.TurnstileCaptchaVerifier;
import com.plataformacitas.shared.ratelimit.RateLimiter;
import com.plataformacitas.support.IntegrationTest;
import com.plataformacitas.support.TestData;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class SecurityHardeningIT extends IntegrationTest {

    static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    TestData data;

    @Autowired
    JsonMapper json;

    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    @AfterEach
    void resetLimits() {
        rateLimiter.resetAll();
    }

    @Test
    void eleventhLoginFromSameIpIsRateLimited() throws Exception {
        TestData.Seeded business = data.business("Límite");
        TestData.SeededUser user = data.user(TestData.email("limite"), "clave-de-prueba-1", true);
        data.member(user, business, "OWNER");
        for (int i = 0; i < 10; i++) {
            assertThat(login(user.email(), "clave-equivocada").statusCode()).isEqualTo(401);
        }
        HttpResponse<String> eleventh = login(user.email(), "clave-de-prueba-1");
        assertThat(eleventh.statusCode()).isEqualTo(429);
        assertThat(json.readTree(eleventh.body()).get("code").asString()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void corsOnlyAllowsConfiguredOrigin() throws Exception {
        HttpResponse<String> evil = preflight("https://evil.example");
        assertThat(evil.statusCode()).isEqualTo(403);
        assertThat(evil.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();

        HttpResponse<String> allowed = preflight("http://localhost:4200");
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:4200");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/auth/me")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("strict-origin-when-cross-origin");
    }

    @Test
    void turnstileSendsFormAndHonoursFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LinkedMultiValueMap<String, String> expected = new LinkedMultiValueMap<>();
        expected.add("secret", "2x0000000000000000000000000000000AA");
        expected.add("response", "token-del-widget");
        expected.add("remoteip", "203.0.113.7");
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(expected))
                .andRespond(withSuccess("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
                        MediaType.APPLICATION_JSON));
        var verifier = new TurnstileCaptchaVerifier(builder, VERIFY_URL, "2x0000000000000000000000000000000AA");
        assertThat(verifier.verify("token-del-widget", "203.0.113.7")).isFalse();
        server.verify();
    }

    @Test
    void turnstileSuccessAndNetworkFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(VERIFY_URL)).andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(VERIFY_URL)).andRespond(withException(new IOException("sin red")));
        var verifier = new TurnstileCaptchaVerifier(builder, VERIFY_URL, "1x0000000000000000000000000000000AA");
        assertThat(verifier.verify(CaptchaVerifier.DUMMY_TOKEN, "203.0.113.7")).isTrue();
        assertThat(verifier.verify(CaptchaVerifier.DUMMY_TOKEN, "203.0.113.7")).isFalse();
        server.verify();
    }

    @Test
    void fakeProviderAcceptsOnlyDummyTokenAndNeverInProd() {
        CaptchaVerifier fake = CaptchaVerifier.Config.create("fake", "", VERIFY_URL, new MockEnvironment());
        assertThat(fake.verify(CaptchaVerifier.DUMMY_TOKEN, "203.0.113.7")).isTrue();
        assertThat(fake.verify("otro-token", "203.0.113.7")).isFalse();

        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> CaptchaVerifier.Config.create("fake", "", VERIFY_URL, prod))
                .isInstanceOf(IllegalStateException.class);
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password));
        return http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/auth/login"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
