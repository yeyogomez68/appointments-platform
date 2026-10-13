package com.plataformacitas.platform.internal;

import com.plataformacitas.platform.Businesses;
import com.plataformacitas.platform.Businesses.BusinessDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Datos del negocio activo (el del token de acceso). */
@RestController
@RequestMapping("/api/v1/business")
@Tag(name = "business")
public class BusinessController {

    public record BusinessResponse(UUID id, String name, String slug, String timezone, String currency, String vertical,
            String status, Instant trialEndsAt, Map<String, String> labels) {}

    public record UpdateBusinessRequest(@NotBlank @Size(min = 2, max = 120) String name, @NotBlank String timezone,
            @NotBlank String currency) {}

    private final Businesses businesses;

    public BusinessController(Businesses businesses) {
        this.businesses = businesses;
    }

    @GetMapping
    public BusinessResponse getBusiness() {
        return response(businesses.current());
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public BusinessResponse updateBusiness(@Valid @RequestBody UpdateBusinessRequest request) {
        return response(businesses.update(request.name(), request.timezone(), request.currency()));
    }

    private static BusinessResponse response(BusinessDetails d) {
        return new BusinessResponse(d.id(), d.name(), d.slug(), d.timezone(), d.currency(), d.vertical(), d.status(),
                d.trialEndsAt(), d.labels());
    }
}
