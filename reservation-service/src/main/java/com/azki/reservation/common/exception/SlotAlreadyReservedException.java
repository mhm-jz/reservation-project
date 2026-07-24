package com.azki.reservation.common.exception;

public final class SlotAlreadyReservedException
        extends BusinessException {

    public SlotAlreadyReservedException(Long slotId) {
        super(
                ErrorCode.SLOT_ALREADY_RESERVED,
                "Slot is already reserved: " + slotId
        );
    }
}
