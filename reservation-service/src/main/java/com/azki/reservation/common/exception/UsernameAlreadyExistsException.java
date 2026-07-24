package com.azki.reservation.common.exception;

public final class UsernameAlreadyExistsException
        extends BusinessException {

    public UsernameAlreadyExistsException() {
        super(ErrorCode.USERNAME_ALREADY_EXISTS);
    }
}
