package com.azki.reservation.exception;

public final class ReservationNotFoundException
        extends BusinessException {

    public ReservationNotFoundException(Long reservationId) {
        super(
                ErrorCode.RESERVATION_NOT_FOUND,
                "Reservation not found: " + reservationId
        );
    }
}
