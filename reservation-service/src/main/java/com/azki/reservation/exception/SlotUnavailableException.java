package com.azki.reservation.exception;

public class SlotUnavailableException
        extends RuntimeException {

    public SlotUnavailableException(Long slotId) {
        super("Slot is no longer available: " + slotId);
    }
}