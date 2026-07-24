package com.azki.reservation.reservation;

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
            AuthenticatedUser authenticatedUser
    ) {
        return reservationService.createReservation(
                request.slotId(),
                authenticatedUser.getId()
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
}
