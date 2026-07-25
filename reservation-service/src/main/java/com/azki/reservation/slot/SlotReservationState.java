package com.azki.reservation.slot;

import java.time.LocalDateTime;

public record SlotReservationState(
        Long id,
        LocalDateTime startTime,
        boolean reserved
) {
}
