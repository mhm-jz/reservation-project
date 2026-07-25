package com.azki.reservation.slot.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "SlotPageResponse",
        description = "Cursor-paginated available slots"
)
public record SlotPageResponse(
        @ArraySchema(
                arraySchema = @Schema(
                        description = "Available slots in this page"
                ),
                schema = @Schema(
                        implementation = AvailableSlotResponse.class
                )
        )
        List<AvailableSlotResponse> items,

        @Schema(
                description = "Next-page cursor, or null",
                example = "eyJzdGFydFRpbWUiOiIyMDI2LTA3LTAxVDEwOjAwOjAwIiwiaWQiOjEyM30",
                nullable = true
        )
        String nextCursor,

        @Schema(
                description = "Whether another page is available",
                example = "true"
        )
        boolean hasNext
) {
}
