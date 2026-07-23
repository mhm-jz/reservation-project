package com.azki.reservation.slot.dto;

import java.time.LocalDateTime;

public record SlotCursor(
        LocalDateTime startTime,
        Long id
) {
}
