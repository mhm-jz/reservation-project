package com.azki.reservation.common.exception;

public final class InvalidIdempotencyKeyException
        extends BusinessException {

    public InvalidIdempotencyKeyException() {
        super(ErrorCode.INVALID_IDEMPOTENCY_KEY);
    }

    public InvalidIdempotencyKeyException(Throwable cause) {
        super(
                ErrorCode.INVALID_IDEMPOTENCY_KEY,
                ErrorCode.INVALID_IDEMPOTENCY_KEY.getDefaultMessage(),
                cause
        );
    }
}
