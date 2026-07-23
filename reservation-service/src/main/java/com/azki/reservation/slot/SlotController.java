package com.azki.reservation.slot;

import com.azki.reservation.slot.dto.SlotPageResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
@Validated
public class SlotController {

    private final SlotService slotService;

    @GetMapping
    public SlotPageResponse getAvailableSlots(
            @RequestParam
            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam
            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int limit,

            @RequestParam(required = false)
            String cursor
    ) {
        return slotService.getAvailableSlots(
                from,
                to,
                limit,
                cursor
        );
    }
}
