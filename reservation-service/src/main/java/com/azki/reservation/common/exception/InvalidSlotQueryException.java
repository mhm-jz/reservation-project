package com.azki.reservation.common.exception;

public final class InvalidSlotQueryException
        extends BusinessException {

    public InvalidSlotQueryException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    public InvalidSlotQueryException(
            String message,
            Throwable cause
    ) {
        super(ErrorCode.VALIDATION_ERROR, message, cause);
    }
}
