package com.plataformacitas.identity.internal;

import com.plataformacitas.identity.internal.AuthController.BusinessOption;
import com.plataformacitas.identity.internal.AuthController.SessionResponse;
import com.plataformacitas.identity.internal.SignupService.CreateBusinessCommand;
import com.plataformacitas.identity.internal.SignupService.CreatedBusiness;
import com.plataformacitas.identity.internal.SignupService.SignupCommand;
import com.plataformacitas.identity.internal.TokenService.IssuedSession;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "signup")
public class SignupController {

    public record SignupRequest(
            @NotBlank @Size(min = 2, max = 120) String businessName,
            @NotBlank @Pattern(regexp = "PELUQUERIA") String vertical,
            @NotBlank String timezone,
            @NotBlank @Size(min = 2, max = 120) String ownerName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128) String password,
            @AssertTrue boolean acceptTerms,
            @AssertTrue boolean acceptDpa,
            @NotBlank String captchaToken) {}

    public record SignupAccepted(String status) {}

    public record VerifySignupRequest(@NotBlank String token) {}

    public record CreateBusinessRequest(
            @NotBlank @Size(min = 2, max = 120) String businessName,
            @NotBlank @Pattern(regexp = "PELUQUERIA") String vertical,
            @NotBlank String timezone,
            @AssertTrue boolean acceptTerms,
            @AssertTrue boolean acceptDpa) {}

    public record PublicConfig(String captchaSiteKey, String termsVersion, String dpaVersion) {}

    private final SignupService signupService;
    private final boolean secureCookie;
    private final Duration refreshTtl;
    private final PublicConfig publicConfig;

    public SignupController(SignupService signupService, @Value("${app.cookie.secure}") boolean secureCookie,
            @Value("${app.jwt.refresh-ttl}") Duration refreshTtl, @Value("${app.captcha.site-key:}") String siteKey,
            @Value("${app.legal.terms-version}") String termsVersion, @Value("${app.legal.dpa-version}") String dpaVersion) {
        this.signupService = signupService;
        this.secureCookie = secureCookie;
        this.refreshTtl = refreshTtl;
        this.publicConfig = new PublicConfig(siteKey == null ? "" : siteKey, termsVersion, dpaVersion);
    }

    @PostMapping("/api/v1/signup")
    public ResponseEntity<SignupAccepted> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
        signupService.signup(new SignupCommand(request.businessName(), request.vertical(), request.timezone(),
                request.ownerName(), request.email(), request.password(), request.acceptTerms(), request.acceptDpa(),
                request.captchaToken()), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new SignupAccepted("VERIFICATION_SENT"));
    }

    @PostMapping("/api/v1/signup/verify")
    public ResponseEntity<SessionResponse> verifySignup(@Valid @RequestBody VerifySignupRequest request) {
        IssuedSession issued = signupService.verify(request.token());
        SessionResponse session = new SessionResponse(issued.accessToken(), "Bearer", issued.expiresInSeconds(),
                new BusinessOption(issued.businessId(), issued.businessName(), issued.slug(), issued.role()));
        ResponseCookie rt = ResponseCookie.from(AuthController.REFRESH_COOKIE, issued.refreshToken())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(AuthController.REFRESH_PATH)
                .maxAge(refreshTtl)
                .build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, rt.toString()).body(session);
    }

    @PostMapping("/api/v1/businesses")
    public ResponseEntity<BusinessOption> createBusiness(@Valid @RequestBody CreateBusinessRequest request,
            JwtAuthenticationToken principal, HttpServletRequest http) {
        CreatedBusiness created = signupService.createAnotherBusiness(UUID.fromString(principal.getToken().getSubject()),
                new CreateBusinessCommand(request.businessName(), request.vertical(), request.timezone(),
                        request.acceptTerms(), request.acceptDpa()), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BusinessOption(created.id(), created.name(), created.slug(), created.role()));
    }

    @GetMapping("/api/v1/public/config")
    public PublicConfig publicConfig() {
        return publicConfig;
    }
}
