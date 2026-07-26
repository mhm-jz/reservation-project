package com.azki.reservation.slot.repository;

import java.time.LocalDateTime;

public record SlotReservationState(
        Long id,
        LocalDateTime startTime
) {
}
