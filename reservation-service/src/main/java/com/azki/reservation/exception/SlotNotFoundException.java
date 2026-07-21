package com.azki.reservation.exception;

public class SlotNotFoundException extends RuntimeException {

    public SlotNotFoundException(Long slotId) {
        super("Slot not found: " + slotId);
    }
}