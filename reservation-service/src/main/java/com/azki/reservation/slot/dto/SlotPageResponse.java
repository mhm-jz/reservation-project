package com.azki.reservation.slot.dto;

import java.util.List;

public record SlotPageResponse(
        List<AvailableSlotResponse> items,
        int page,
        int size,
        boolean hasNext
) {
}