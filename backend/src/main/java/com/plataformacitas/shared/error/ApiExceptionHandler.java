package com.plataformacitas.shared.error;

import com.plataformacitas.shared.error.DomainException.Code;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** Toda respuesta de error es un ProblemDetail con {@code code}, {@code title}, {@code detail} y {@code requestId}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> domain(DomainException ex) {
        return problem(ex.code(), ex.code().status(), ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.add(fieldError(e.getField(), e.getDefaultMessage())));
        return problem(Code.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "La solicitud tiene campos inválidos.", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> invalidParameters(HandlerMethodValidationException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(e ->
                errors.add(fieldError(r.getMethodParameter().getParameterName(), e.getDefaultMessage()))));
        return problem(Code.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "La solicitud tiene campos inválidos.", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex) {
        return problem(Code.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(AccessDeniedException ex) {
        return problem(Code.FORBIDDEN, HttpStatus.FORBIDDEN, "No tienes permiso para esta acción.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex) {
        String sqlState = sqlState(ex);
        if ("23P01".equals(sqlState)) {
            return problem(Code.SLOT_TAKEN, HttpStatus.CONFLICT, "El horario ya está ocupado.", null);
        }
        if ("23505".equals(sqlState)) {
            return problem(Code.CONFLICT, HttpStatus.CONFLICT, "El registro ya existe.", null);
        }
        return unexpected(ex);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        // Excepciones propias de Spring MVC (ruta inexistente, método no permitido…): conservan su estado 4xx.
        if (ex instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = framework.getStatusCode();
            return status.value() == 404
                    ? problem(Code.NOT_FOUND, status, "El recurso no existe.", null)
                    : problem(Code.VALIDATION_ERROR, status, "La solicitud no es válida.", null);
        }
        log.error("Error no controlado", ex);
        return problem(Code.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.", null);
    }

    private static ResponseEntity<ProblemDetail> problem(Code code, HttpStatusCode status, String detail,
            List<Map<String, String>> errors) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(code.name());
        body.setProperty("code", code.name());
        body.setProperty("requestId", MDC.get("requestId"));
        if (errors != null) {
            body.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).body(body);
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message == null ? "" : message);
    }

    private static String sqlState(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
