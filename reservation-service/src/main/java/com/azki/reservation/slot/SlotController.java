package com.azki.reservation.slot;

import com.azki.reservation.slot.dto.SlotPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping(
        value = "/api/slots",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
@Validated
@Tag(
        name = "Slots",
        description = "Browse available reservation slots"
)
public class SlotController {

    private final SlotService slotService;

    @GetMapping
    @Operation(
            summary = "List available reservation slots",
            description = """
                    Returns available slots inside the requested time range.

                    Results are ordered by start time and ID.
                    Cursor-based pagination is used to retrieve the next page.
                    """
    )
    @ApiResponse(
            responseCode = "400",
            ref = "#/components/responses/SlotQueryBadRequest"
    )
    public SlotPageResponse getAvailableSlots(
            @RequestParam
            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(
                    description = "Inclusive start of the requested time range",
                    required = true,
                    example = "2026-07-01T00:00:00"
            )
            LocalDateTime from,

            @RequestParam
            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(
                    description = "Exclusive end of the requested time range",
                    required = true,
                    example = "2026-07-31T00:00:00"
            )
            LocalDateTime to,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            @Parameter(
                    description = "Maximum number of slots to return",
                    example = "20"
            )
            int limit,

            @RequestParam(required = false)
            @Parameter(
                    description = "Opaque cursor returned by the previous page",
                    example = "eyJzdGFydFRpbWUiOiIyMDI2LTA3LTAxVDEwOjAwOjAwIiwiaWQiOjEyM30"
            )
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
