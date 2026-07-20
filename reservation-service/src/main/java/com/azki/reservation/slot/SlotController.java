package com.azki.reservation.slot;

import com.azki.reservation.slot.dto.SlotPageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
@Validated
public class SlotController {

    private final SlotService slotService;

    @GetMapping
    public SlotPageResponse getAvailableSlots(

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size
    ) {
        return slotService.getAvailableSlots(page, size);
    }
}