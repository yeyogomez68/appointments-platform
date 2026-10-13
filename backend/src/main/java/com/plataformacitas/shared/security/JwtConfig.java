package com.plataformacitas.shared.security;

import com.plataformacitas.shared.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Firma y validación de JWT HS256 con {@code JWT_SECRET}, y enlace del token al {@link TenantContext}. */
@Configuration
public class JwtConfig {

    public static final String TYP_ACCESS = "access";
    public static final String TYP_PREAUTH = "preauth";
    private static final Pattern HEX_64 = Pattern.compile("^(?:[0-9a-fA-F]{2}){32,}$");

    /** Exige 64 caracteres hexadecimales o más (32 bytes). */
    public static SecretKey secretKey(String hex) {
        if (hex == null || !HEX_64.matcher(hex.trim()).matches()) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 64 caracteres hexadecimales (32 bytes)");
        }
        return new SecretKeySpec(HexFormat.of().parseHex(hex.trim()), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String hex) {
        return NimbusJwtEncoder.withSecretKey(secretKey(hex)).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String hex) {
        return NimbusJwtDecoder.withSecretKey(secretKey(hex)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            String typ = jwt.getClaimAsString("typ");
            if (TYP_PREAUTH.equals(typ)) {
                authorities.add(new SimpleGrantedAuthority("PREAUTH"));
            } else if (TYP_ACCESS.equals(typ)) {
                authorities.add(new SimpleGrantedAuthority("TENANT"));
                String role = jwt.getClaimAsString("role");
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    /** Con un token de acceso fija negocio y cuenta para la petición; los limpia al terminar. */
    public static final class TenantBindingFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            try {
                if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                        && TYP_ACCESS.equals(token.getToken().getClaimAsString("typ"))) {
                    String bid = token.getToken().getClaimAsString("bid");
                    TenantContext.set(UUID.fromString(bid), UUID.fromString(token.getToken().getSubject()));
                    MDC.put("businessId", bid);
                }
                chain.doFilter(request, response);
            } finally {
                TenantContext.clear();
                MDC.remove("businessId");
            }
        }
    }
}
