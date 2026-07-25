package com.azki.reservation.reservation.repository;

import java.time.LocalDateTime;

public record OwnedReservationSlot(
        Long slotId,
        LocalDateTime startTime
) {
}
