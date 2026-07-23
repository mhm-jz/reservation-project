package com.azki.reservation.slot.dto;

import java.util.List;

public record SlotPageResponse(
        List<AvailableSlotResponse> items,
        String nextCursor,
        boolean hasNext
) {
}
