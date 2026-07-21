package com.azki.reservation.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateReservationRequest(

        @NotNull
        @Positive
        Long slotId
) {
}