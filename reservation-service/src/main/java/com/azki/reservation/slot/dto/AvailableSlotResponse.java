package com.azki.reservation.slot.dto;

import java.time.LocalDateTime;

public record AvailableSlotResponse(
        Long id,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}