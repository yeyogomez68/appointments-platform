package com.plataformacitas.shared.captcha;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.client.RestClient;

/** Verificación de captcha del registro. */
public interface CaptchaVerifier {

    /** Token de prueba de Cloudflare Turnstile; el proveedor {@code fake} solo acepta este. */
    String DUMMY_TOKEN = "XXXX.DUMMY.TOKEN.XXXX";

    boolean verify(String token, String remoteIp);

    @Configuration(proxyBeanMethods = false)
    class Config {

        @Bean
        CaptchaVerifier captchaVerifier(@Value("${app.captcha.provider}") String provider,
                @Value("${app.captcha.secret:}") String secret, @Value("${app.captcha.verify-url}") String verifyUrl,
                Environment env) {
            return create(provider, secret, verifyUrl, env);
        }

        public static CaptchaVerifier create(String provider, String secret, String verifyUrl, Environment env) {
            return switch (provider) {
                case "fake" -> {
                    if (env.acceptsProfiles(Profiles.of("prod"))) {
                        throw new IllegalStateException("CAPTCHA_PROVIDER=fake no se permite con el perfil prod");
                    }
                    yield (token, ip) -> DUMMY_TOKEN.equals(token);
                }
                case "turnstile" -> {
                    if (secret == null || secret.isBlank()) {
                        throw new IllegalStateException("TURNSTILE_SECRET es obligatorio con CAPTCHA_PROVIDER=turnstile");
                    }
                    yield new TurnstileCaptchaVerifier(RestClient.builder(), verifyUrl, secret);
                }
                default -> throw new IllegalStateException("CAPTCHA_PROVIDER desconocido: " + provider);
            };
        }
    }
}
