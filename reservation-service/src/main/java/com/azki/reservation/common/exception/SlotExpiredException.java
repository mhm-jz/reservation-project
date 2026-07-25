package com.azki.reservation.common.exception;

public final class SlotExpiredException extends BusinessException {

    public SlotExpiredException(Long slotId) {
        super(
                ErrorCode.SLOT_EXPIRED,
                "Slot has expired: " + slotId
        );
    }
}
