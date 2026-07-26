package com.azki.reservation.reservation.controller;

import com.azki.reservation.common.exception.InvalidIdempotencyKeyException;
import com.azki.reservation.reservation.dto.CreateReservationRequest;
import com.azki.reservation.reservation.dto.ReservationResponse;
import com.azki.reservation.reservation.service.ReservationService;
import com.azki.reservation.security.model.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(
        name = "Reservations",
        description = "Create and cancel authenticated users' reservations"
)
public class ReservationController {

    private static final String IDEMPOTENCY_DESCRIPTION = """
            Optional per-user UUID. Omit for the non-idempotent flow. Retrying
            the same slot returns the original HTTP 201 snapshot; another slot
            returns 409. Different users have independent keys. Failed attempts
            are not stored. Cancellation keeps the snapshot. Use a new UUID for
            each new operation.
            """;

    private final ReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Reserve an available slot",
            description = IDEMPOTENCY_DESCRIPTION
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Created or successfully replayed",
                    content = @Content(
                            schema = @Schema(
                                    implementation =
                                            ReservationResponse.class
                            ),
                            examples = @ExampleObject(
                                    name = "reservation",
                                    value = """
                                            {
                                              "id": 987,
                                              "slotId": 123,
                                              "userId": 42,
                                              "startTime": "2026-07-28T10:00:00",
                                              "endTime": "2026-07-28T10:30:00",
                                              "createdAt": "2026-07-25T14:15:30"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    ref = "#/components/responses/ReservationBadRequest"
            ),
            @ApiResponse(
                    responseCode = "404",
                    ref = "#/components/responses/SlotNotFound"
            ),
            @ApiResponse(
                    responseCode = "409",
                    ref = "#/components/responses/SlotReservationConflict"
            ),
            @ApiResponse(
                    responseCode = "500",
                    ref = "#/components/responses/InternalServerError"
            )
    })
    public ReservationResponse createReservation(
            @Valid @RequestBody
            CreateReservationRequest request,

            @Parameter(hidden = true)
            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser,

            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            )
            @Parameter(
                    description = IDEMPOTENCY_DESCRIPTION,
                    example = "550e8400-e29b-41d4-a716-446655440000",
                    schema = @Schema(
                            type = "string",
                            format = "uuid"
                    )
            )
            String idempotencyKey
    ) {
        UUID parsedIdempotencyKey =
                parseIdempotencyKey(idempotencyKey);

        return parsedIdempotencyKey == null
                ? reservationService.createReservation(
                        request.slotId(),
                        authenticatedUser.getId()
                )
                : reservationService.createReservation(
                        request.slotId(),
                        authenticatedUser.getId(),
                        parsedIdempotencyKey
                );
    }

    @DeleteMapping("/{reservationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Cancel an existing reservation",
            description = "Cancels an owned reservation. Missing, cancelled, or non-owned IDs return 404."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Reservation cancelled successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    ref = "#/components/responses/ReservationCancellationNotFound"
            ),
            @ApiResponse(
                    responseCode = "500",
                    ref = "#/components/responses/InternalServerError"
            )
    })
    public void cancelReservation(
            @Parameter(
                    description = "Reservation ID to cancel",
                    example = "987"
            )
            @PathVariable Long reservationId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser
    ) {
        reservationService.cancelReservation(
                reservationId,
                authenticatedUser.getId()
        );
    }

    private UUID parseIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        if (idempotencyKey.isBlank()) {
            throw new InvalidIdempotencyKeyException();
        }

        try {
            UUID parsed = UUID.fromString(idempotencyKey);
            if (!parsed.toString().equalsIgnoreCase(idempotencyKey)) {
                throw new InvalidIdempotencyKeyException();
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new InvalidIdempotencyKeyException(exception);
        }
    }
}
