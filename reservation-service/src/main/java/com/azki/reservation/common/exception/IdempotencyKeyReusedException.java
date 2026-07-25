package com.azki.reservation.common.exception;

public final class IdempotencyKeyReusedException
        extends BusinessException {

    public IdempotencyKeyReusedException() {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }
}
