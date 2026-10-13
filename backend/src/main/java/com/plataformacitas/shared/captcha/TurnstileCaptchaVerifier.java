package com.plataformacitas.shared.captcha;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Cloudflare Turnstile (siteverify). Ante cualquier error de red o de formato, falla cerrada. */
public class TurnstileCaptchaVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileCaptchaVerifier.class);

    record SiteVerifyResponse(boolean success, @JsonProperty("error-codes") List<String> errorCodes) {}

    private final RestClient client;
    private final String verifyUrl;
    private final String secret;

    public TurnstileCaptchaVerifier(RestClient.Builder builder, String verifyUrl, String secret) {
        this.client = builder.build();
        this.verifyUrl = verifyUrl;
        this.secret = secret;
    }

    @Override
    public boolean verify(String token, String remoteIp) {
        if (token == null || token.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        if (remoteIp != null) {
            form.add("remoteip", remoteIp);
        }
        try {
            SiteVerifyResponse response = client.post()
                    .uri(verifyUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SiteVerifyResponse.class);
            if (response == null || !response.success()) {
                log.warn("Turnstile rechazó el token: {}", response == null ? "sin cuerpo" : response.errorCodes());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("Turnstile no respondió correctamente: {}", e.getMessage());
            return false;
        }
    }
}
