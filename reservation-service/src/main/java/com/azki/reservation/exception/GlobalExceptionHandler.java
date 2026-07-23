package com.azki.reservation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            InvalidSlotQueryException.class,
            ConstraintViolationException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidRequest(
            RuntimeException exception
    ) {
        return new ErrorResponse(
                "VALIDATION_ERROR",
                exception.getMessage(),
                Instant.now()
        );
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUsernameAlreadyExists(
            UsernameAlreadyExistsException exception
    ) {
        return new ErrorResponse(
                "USERNAME_ALREADY_EXISTS",
                exception.getMessage(),
                Instant.now()
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials() {
        return new ErrorResponse(
                "INVALID_CREDENTIALS",
                "Username or password is incorrect",
                Instant.now()
        );
    }

    @ExceptionHandler(SlotNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSlotNotFound(
            SlotNotFoundException exception
    ) {
        return new ErrorResponse(
                "SLOT_NOT_FOUND",
                exception.getMessage(),
                Instant.now()
        );
    }

    @ExceptionHandler(SlotAlreadyReservedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSlotAlreadyReserved(
            SlotAlreadyReservedException exception
    ) {
        return new ErrorResponse(
                "SLOT_ALREADY_RESERVED",
                exception.getMessage(),
                Instant.now()
        );
    }

    @ExceptionHandler(SlotUnavailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSlotUnavailable(
            SlotUnavailableException exception
    ) {
        return new ErrorResponse(
                "SLOT_UNAVAILABLE",
                exception.getMessage(),
                Instant.now()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error ->
                        error.getField() + ": " +
                                error.getDefaultMessage()
                )
                .orElse("Request validation failed");

        return new ErrorResponse(
                "VALIDATION_ERROR",
                message,
                Instant.now()
        );
    }


    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleReservationNotFound(
            ReservationNotFoundException exception
    ) {
        return new ErrorResponse(
                "RESERVATION_NOT_FOUND",
                exception.getMessage(),
                Instant.now()
        );
    }


    @ExceptionHandler(ReservationStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleReservationState(
            ReservationStateException exception
    ) {
        return new ErrorResponse(
                "RESERVATION_STATE_ERROR",
                "Reservation could not be cancelled",
                Instant.now()
        );
    }
}
