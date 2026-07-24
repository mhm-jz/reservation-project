package com.azki.reservation.reservation;

import java.time.LocalDateTime;

public record OwnedReservationSlot(
        Long slotId,
        LocalDateTime startTime
) {
}
