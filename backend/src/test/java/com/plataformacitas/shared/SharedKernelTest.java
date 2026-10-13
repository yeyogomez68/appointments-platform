package com.plataformacitas.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plataformacitas.shared.error.ApiExceptionHandler;
import com.plataformacitas.shared.error.DomainException;
import com.plataformacitas.shared.error.DomainException.Code;
import com.plataformacitas.shared.money.Money;
import com.plataformacitas.shared.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class SharedKernelTest {

    record ProbeRequest(@NotBlank String name, @Email String email) {}

    /**
     * Spring Framework 7 solo mapea clases con {@code @Controller}, incluso en MockMvc standalone. Al estar anidada
     * en una clase de prueba, el {@code TestTypeExcludeFilter} de Spring Boot la deja fuera del escaneo de componentes.
     */
    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @PostMapping("/validate")
        String validate(@Valid @RequestBody ProbeRequest request) {
            return "ok";
        }

        @GetMapping("/not-found")
        String notFound() {
            throw new DomainException(Code.NOT_FOUND, "La cita no existe.");
        }

        @GetMapping("/slot-taken")
        String slotTaken() {
            throw new DataIntegrityViolationException("x", new SQLException("solapado", "23P01"));
        }
    }

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void mixingCurrenciesFails() {
        DomainException ex = assertThrows(DomainException.class,
                () -> Money.of(1000, "COP").plus(Money.of(500, "USD")));
        assertThat(ex.code()).isEqualTo(Code.CURRENCY_MISMATCH);
    }

    @Test
    void ofMajorRoundsHalfEven() {
        assertThat(Money.ofMajor(new BigDecimal("10.005"), "USD").amountMinor()).isEqualTo(1000);
        assertThat(Money.ofMajor(new BigDecimal("10.015"), "USD").amountMinor()).isEqualTo(1002);
    }

    @Test
    void arithmeticKeepsCurrency() {
        Money total = Money.of(1000, "COP").plus(Money.of(500, "COP")).times(2).minus(Money.of(4000, "COP"));
        assertThat(total).isEqualTo(Money.of(-1000, "COP"));
        assertThat(total.isNegative()).isTrue();
    }

    @Test
    void validationErrorNamesEveryInvalidField() throws Exception {
        mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", Matchers.containsInAnyOrder("name", "email")))
                .andExpect(jsonPath("$.errors[*].message", Matchers.everyItem(Matchers.not(Matchers.emptyString()))));
    }

    @Test
    void domainNotFoundIs404WithProblemBody() throws Exception {
        mvc.perform(get("/probe/not-found").header("X-Request-Id", "abc-123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").value("La cita no existe."))
                .andExpect(jsonPath("$.requestId").value("abc-123"));
    }

    @Test
    void exclusionViolationIsSlotTaken() throws Exception {
        mvc.perform(get("/probe/slot-taken"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
    }

    @Test
    void requestIdIsEchoedOrGenerated() throws Exception {
        mvc.perform(get("/probe/slot-taken").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"))
                .andExpect(jsonPath("$.requestId").value("abc-123"));

        String id = mvc.perform(get("/probe/slot-taken")).andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    }
}
