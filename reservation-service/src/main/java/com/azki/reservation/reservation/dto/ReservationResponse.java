package com.azki.reservation.reservation.dto;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long slotId,
        Long userId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt
) {
}