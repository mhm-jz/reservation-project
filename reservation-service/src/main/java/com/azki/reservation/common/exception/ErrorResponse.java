package com.azki.reservation.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(
        name = "ErrorResponse",
        description = "Standard API error response"
)
public record ErrorResponse(
        @Schema(
                description = "Stable ErrorCode name",
                example = "IDEMPOTENCY_KEY_REUSED"
        )
        String code,

        @Schema(
                description = "Human-readable error detail",
                example = "Idempotency-Key was already used for a different request"
        )
        String message,

        @Schema(
                description = "Time at which the error response was created",
                example = "2026-07-25T12:15:30Z"
        )
        Instant timestamp
) {
}
