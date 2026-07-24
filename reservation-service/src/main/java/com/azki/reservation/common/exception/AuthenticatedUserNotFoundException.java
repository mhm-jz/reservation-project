package com.azki.reservation.common.exception;

public final class AuthenticatedUserNotFoundException
        extends BusinessException {

    public AuthenticatedUserNotFoundException() {
        super(ErrorCode.UNAUTHORIZED);
    }
}
