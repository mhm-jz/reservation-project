package com.azki.reservation.exception;

public class ReservationStateException extends RuntimeException {

    public ReservationStateException(Long reservationId) {
        super(
                "Reservation state is inconsistent: "
                        + reservationId
        );
    }
}