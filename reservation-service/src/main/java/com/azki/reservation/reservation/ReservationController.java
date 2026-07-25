package com.azki.reservation.reservation;

import com.azki.reservation.common.exception.InvalidIdempotencyKeyException;
import com.azki.reservation.reservation.dto.CreateReservationRequest;
import com.azki.reservation.reservation.dto.ReservationResponse;
import com.azki.reservation.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Reservations")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reserve an available slot")
    @ApiResponses({
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
    @Operation(summary = "Cancel an existing reservation")
    @ApiResponses({
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
            @Parameter(description = "Reservation ID to cancel")
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
