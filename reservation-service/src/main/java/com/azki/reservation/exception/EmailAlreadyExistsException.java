package com.azki.reservation.exception;

public final class EmailAlreadyExistsException
        extends BusinessException {

    public EmailAlreadyExistsException() {
        super(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
