package com.azki.reservation.slot;

import com.azki.reservation.slot.dto.SlotPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    @SecurityRequirements
    @Operation(
            summary = "List available reservation slots",
            description = """
                    UTC LocalDateTime values without an offset. Lists slots in
                    `[from, to)`, ordered by `startTime`, then `id`. Omit
                    `cursor` for page one; pass `nextCursor` unchanged.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page of available slots",
                    content = @Content(
                            schema = @Schema(
                                    implementation = SlotPageResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    ref = "#/components/responses/SlotQueryBadRequest"
            )
    })
    public SlotPageResponse getAvailableSlots(
            @RequestParam
            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(
                    description = "Inclusive UTC date-time without an offset",
                    required = true,
                    example = "2026-07-01T00:00:00",
                    schema = @Schema(
                            type = "string",
                            format = "date-time"
                    )
            )
            LocalDateTime from,

            @RequestParam
            @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Parameter(
                    description = "Exclusive UTC date-time without an offset; maximum range is 30 days",
                    required = true,
                    example = "2026-07-31T00:00:00",
                    schema = @Schema(
                            type = "string",
                            format = "date-time"
                    )
            )
            LocalDateTime to,

            @RequestParam(
                    defaultValue =
                            "${app.slot-search.default-page-size}"
            )
            @Min(1)
            @Parameter(
                    description = "Page size; defaults to 20",
                    example = "20",
                    schema = @Schema(
                            minimum = "1",
                            maximum = "100",
                            defaultValue = "20"
                    )
            )
            int limit,

            @RequestParam(required = false)
            @Parameter(
                    description = "Opaque cursor containing the same UTC LocalDateTime representation; pass unchanged",
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
