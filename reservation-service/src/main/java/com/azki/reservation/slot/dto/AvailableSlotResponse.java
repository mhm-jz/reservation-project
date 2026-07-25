package com.azki.reservation.slot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(
        name = "AvailableSlotResponse",
        description = "An available reservation slot"
)
public record AvailableSlotResponse(
        @Schema(description = "Slot ID", example = "123")
        Long id,

        @Schema(
                description = "UTC slot start time without an offset",
                example = "2026-07-28T10:00:00"
        )
        LocalDateTime startTime,

        @Schema(
                description = "UTC slot end time without an offset",
                example = "2026-07-28T10:30:00"
        )
        LocalDateTime endTime
) {
}
