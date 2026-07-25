package com.azki.reservation.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(
        name = "ReservationResponse",
        description = "Successful reservation snapshot"
)
public record ReservationResponse(
        @Schema(description = "Reservation ID", example = "987")
        Long id,

        @Schema(description = "Reserved slot ID", example = "123")
        Long slotId,

        @Schema(description = "Authenticated owner's user ID", example = "42")
        Long userId,

        @Schema(
                description = "UTC slot start time without an offset",
                example = "2026-07-28T10:00:00"
        )
        LocalDateTime startTime,

        @Schema(
                description = "UTC slot end time without an offset",
                example = "2026-07-28T10:30:00"
        )
        LocalDateTime endTime,

        @Schema(
                description = "UTC creation time without an offset",
                example = "2026-07-25T14:15:30"
        )
        LocalDateTime createdAt
) {
}
