package com.plataformacitas.shared.error;

import org.springframework.http.HttpStatus;

/** Error de negocio con un código estable que el cliente puede interpretar. */
public class DomainException extends RuntimeException {

    public enum Code {
        VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
        INVALID_TOKEN(HttpStatus.BAD_REQUEST),
        CAPTCHA_FAILED(HttpStatus.BAD_REQUEST),
        UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
        INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
        INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
        FORBIDDEN(HttpStatus.FORBIDDEN),
        EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
        NO_ACTIVE_MEMBERSHIP(HttpStatus.FORBIDDEN),
        NOT_FOUND(HttpStatus.NOT_FOUND),
        ACCOUNT_EXISTS(HttpStatus.CONFLICT),
        CONFLICT(HttpStatus.CONFLICT),
        SLOT_TAKEN(HttpStatus.CONFLICT),
        CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT),
        RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
        INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

        private final HttpStatus status;

        Code(HttpStatus status) {
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }

    private final Code code;

    public DomainException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
