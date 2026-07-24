package com.azki.reservation.common.exception;

public final class SlotUnavailableException
        extends BusinessException {

    public SlotUnavailableException(Long slotId) {
        super(
                ErrorCode.SLOT_UNAVAILABLE,
                "Slot is no longer available: " + slotId
        );
    }
}
