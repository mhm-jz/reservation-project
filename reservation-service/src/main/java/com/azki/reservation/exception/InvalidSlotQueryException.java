package com.azki.reservation.exception;

public class InvalidSlotQueryException extends RuntimeException {

    public InvalidSlotQueryException(String message) {
        super(message);
    }

    public InvalidSlotQueryException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
