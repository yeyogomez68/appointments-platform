package com.plataformacitas.identity.internal;

import com.plataformacitas.identity.internal.AuthService.LoginResult;
import com.plataformacitas.identity.internal.AuthService.LoginStatus;
import com.plataformacitas.identity.internal.AuthService.MeView;
import com.plataformacitas.identity.internal.IdentityStore.MembershipView;
import com.plataformacitas.identity.internal.TokenService.IssuedSession;
import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth")
public class AuthController {

    static final String REFRESH_COOKIE = "rt";
    static final String REFRESH_PATH = "/api/v1/auth";

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}

    public record BusinessOption(UUID id, String name, String slug, String role) {}

    public record SessionResponse(String accessToken, String tokenType, long expiresIn, BusinessOption business) {}

    public record LoginResponse(String status, String code, SessionResponse session, String preAuthToken,
            List<BusinessOption> businesses) {}

    public record SelectBusinessRequest(@NotNull UUID businessId) {}

    public record UserSummary(UUID id, String email, String name) {}

    public record MeResponse(UserSummary user, BusinessOption business, List<BusinessOption> businesses) {}

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 10, max = 128) String newPassword) {}

    private final AuthService auth;
    private final TokenService tokens;
    private final PasswordResetService passwordReset;
    private final boolean secureCookie;
    private final Duration refreshTtl;

    public AuthController(AuthService auth, TokenService tokens, PasswordResetService passwordReset, @Value("${app.cookie.secure}") boolean secureCookie,
            @Value("${app.jwt.refresh-ttl}") Duration refreshTtl) {
        this.auth = auth;
        this.tokens = tokens;
        this.passwordReset = passwordReset;
        this.secureCookie = secureCookie;
        this.refreshTtl = refreshTtl;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = auth.login(request.email(), request.password());
        if (result.status() == LoginStatus.AUTHENTICATED) {
            return withSession(result.session(), session -> new LoginResponse(LoginStatus.AUTHENTICATED.name(), null,
                    session, null, null));
        }
        String selection = LoginStatus.BUSINESS_SELECTION_REQUIRED.name();
        return ResponseEntity.ok(new LoginResponse(selection, selection, null, result.preAuthToken(),
                result.businesses().stream().map(AuthController::option).toList()));
    }

    @PostMapping("/select-business")
    public ResponseEntity<SessionResponse> selectBusiness(@Valid @RequestBody SelectBusinessRequest request,
            JwtAuthenticationToken principal) {
        return withSession(auth.selectBusiness(userId(principal), request.businessId()), session -> session);
    }

    @PostMapping("/switch-business")
    public ResponseEntity<SessionResponse> switchBusiness(@Valid @RequestBody SelectBusinessRequest request,
            JwtAuthenticationToken principal, @CookieValue(name = REFRESH_COOKIE, required = false) String refresh) {
        return withSession(auth.switchBusiness(userId(principal), request.businessId(), refresh), session -> session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh) {
        if (refresh == null || refresh.isBlank()) {
            throw new DomainException(Code.INVALID_REFRESH_TOKEN, "La sesión expiró. Inicia sesión de nuevo.");
        }
        return withSession(tokens.rotate(refresh), session -> session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh) {
        tokens.revokeFamily(refresh);
        ResponseCookie cleared = cookie("").maxAge(0).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        passwordReset.requestReset(request.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(JwtAuthenticationToken principal) {
        MeView me = auth.me(userId(principal), UUID.fromString(principal.getToken().getClaimAsString("bid")));
        return new MeResponse(new UserSummary(me.userId(), me.email(), me.name()), option(me.business()),
                me.businesses().stream().map(AuthController::option).toList());
    }

    private <T> ResponseEntity<T> withSession(IssuedSession issued, java.util.function.Function<SessionResponse, T> body) {
        SessionResponse session = new SessionResponse(issued.accessToken(), "Bearer", issued.expiresInSeconds(),
                new BusinessOption(issued.businessId(), issued.businessName(), issued.slug(), issued.role()));
        ResponseCookie rt = cookie(issued.refreshToken()).maxAge(refreshTtl).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, rt.toString()).body(body.apply(session));
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(REFRESH_PATH);
    }

    private static UUID userId(JwtAuthenticationToken principal) {
        return UUID.fromString(principal.getToken().getSubject());
    }

    private static BusinessOption option(MembershipView m) {
        return new BusinessOption(m.businessId(), m.businessName(), m.slug(), m.role());
    }
}
