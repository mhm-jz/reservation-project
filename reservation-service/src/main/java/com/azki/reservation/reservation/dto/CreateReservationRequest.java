package com.azki.reservation.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(
        name = "CreateReservationRequest",
        description = "Slot to reserve",
        example = "{\"slotId\":123}"
)
public record CreateReservationRequest(

        @NotNull
        @Positive
        @Schema(
                description = "Positive ID of the available slot",
                example = "123",
                minimum = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Long slotId
) {
}
