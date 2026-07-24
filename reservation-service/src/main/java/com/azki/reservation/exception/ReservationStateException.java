package com.azki.reservation.exception;

public final class ReservationStateException
        extends BusinessException {

    public ReservationStateException() {
        super(ErrorCode.RESERVATION_STATE_ERROR);
    }
}
