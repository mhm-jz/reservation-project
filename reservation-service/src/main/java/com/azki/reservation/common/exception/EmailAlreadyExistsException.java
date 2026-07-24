package com.azki.reservation.common.exception;

public final class EmailAlreadyExistsException
        extends BusinessException {

    public EmailAlreadyExistsException() {
        super(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
