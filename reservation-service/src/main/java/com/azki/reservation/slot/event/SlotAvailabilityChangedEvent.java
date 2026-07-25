package com.azki.reservation.slot.event;

import java.time.LocalDate;

public record SlotAvailabilityChangedEvent(LocalDate day) {
}
