package com.plataformacitas.shared.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Límites en memoria del proceso (una sola instancia de API). Las pruebas llaman {@link #resetAll()}. */
@Component
public class RateLimiter {

    public enum Policy { LOGIN_FAILURES, SIGNUP, FORGOT_PASSWORD_IP, FORGOT_PASSWORD_EMAIL }

    private static final int MAX_ENTRIES = 100_000;

    private final Map<Policy, Bandwidth> limits;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${app.rate-limit.login-failures-per-minute}") int loginFailuresPerMinute,
            @Value("${app.rate-limit.signup-per-hour}") int signupPerHour,
            @Value("${app.rate-limit.forgot-password-per-hour}") int forgotPerHour,
            @Value("${app.rate-limit.forgot-password-per-email-per-hour}") int forgotPerEmailPerHour) {
        this.limits = Map.of(
                Policy.LOGIN_FAILURES, bandwidth(loginFailuresPerMinute, Duration.ofMinutes(1)),
                Policy.SIGNUP, bandwidth(signupPerHour, Duration.ofHours(1)),
                Policy.FORGOT_PASSWORD_IP, bandwidth(forgotPerHour, Duration.ofHours(1)),
                Policy.FORGOT_PASSWORD_EMAIL, bandwidth(forgotPerEmailPerHour, Duration.ofHours(1)));
    }

    public boolean tryConsume(Policy policy, String key) {
        return bucket(policy, key).tryConsume(1);
    }

    public boolean isExhausted(Policy policy, String key) {
        return bucket(policy, key).getAvailableTokens() == 0;
    }

    public void resetAll() {
        buckets.clear();
    }

    private Bucket bucket(Policy policy, String key) {
        if (buckets.size() > MAX_ENTRIES) {
            buckets.clear();
        }
        return buckets.computeIfAbsent(policy + ":" + key,
                k -> Bucket.builder().addLimit(limits.get(policy)).build());
    }

    private static Bandwidth bandwidth(int capacity, Duration period) {
        return Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build();
    }
}
