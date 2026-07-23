package com.azki.reservation.slot;

import java.time.LocalDate;

public record SlotAvailabilityChangedEvent(LocalDate day) {
}
