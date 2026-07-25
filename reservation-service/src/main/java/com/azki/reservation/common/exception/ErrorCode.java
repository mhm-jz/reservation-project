package com.azki.reservation.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "Request validation failed"
    ),
    USERNAME_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "Username already exists"
    ),
    EMAIL_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "Email already exists"
    ),
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "Username or password is incorrect"
    ),
    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Authentication is required"
    ),
    SLOT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Slot not found"
    ),
    SLOT_ALREADY_RESERVED(
            HttpStatus.CONFLICT,
            "Slot is already reserved"
    ),
    SLOT_UNAVAILABLE(
            HttpStatus.CONFLICT,
            "Slot is no longer available"
    ),
    SLOT_EXPIRED(
            HttpStatus.CONFLICT,
            "Slot has expired"
    ),
    INVALID_IDEMPOTENCY_KEY(
            HttpStatus.BAD_REQUEST,
            "Idempotency-Key must be a valid UUID"
    ),
    IDEMPOTENCY_KEY_REUSED(
            HttpStatus.CONFLICT,
            "Idempotency-Key was already used for a different request"
    ),
    RESERVATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Reservation not found"
    ),
    RESERVATION_STATE_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Reservation could not be cancelled"
    ),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
    );

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(
            HttpStatus httpStatus,
            String defaultMessage
    ) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
