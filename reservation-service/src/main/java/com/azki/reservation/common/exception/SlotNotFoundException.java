package com.azki.reservation.common.exception;

public final class SlotNotFoundException
        extends BusinessException {

    public SlotNotFoundException(Long slotId) {
        super(
                ErrorCode.SLOT_NOT_FOUND,
                "Slot not found: " + slotId
        );
    }
}
