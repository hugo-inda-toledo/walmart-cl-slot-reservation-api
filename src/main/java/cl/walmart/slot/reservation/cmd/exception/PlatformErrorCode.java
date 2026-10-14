package cl.walmart.slot.reservation.cmd.exception;

import cl.walmart.slot.reservation.domain.exception.ErrorCode;

public enum PlatformErrorCode implements ErrorCode {
    MALFORMED_REQUEST,
    VALIDATION_ERROR,
    RESOURCE_NOT_FOUND,
    METHOD_NOT_ALLOWED,
    NOT_ACCEPTABLE,
    UNSUPPORTED_MEDIA_TYPE,
    CORS_REJECTED,
    INTERNAL_ERROR;

    @Override
    public String value() {
        return name();
    }
}
