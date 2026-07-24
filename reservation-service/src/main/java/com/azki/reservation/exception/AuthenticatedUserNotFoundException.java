package com.azki.reservation.exception;

public final class AuthenticatedUserNotFoundException
        extends BusinessException {

    public AuthenticatedUserNotFoundException() {
        super(ErrorCode.UNAUTHORIZED);
    }
}
