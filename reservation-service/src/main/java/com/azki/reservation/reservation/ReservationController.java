package com.azki.reservation.reservation;

import com.azki.reservation.reservation.dto.CreateReservationRequest;
import com.azki.reservation.reservation.dto.ReservationResponse;
import com.azki.reservation.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse createReservation(
            @Valid @RequestBody
            CreateReservationRequest request,

            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser
    ) {
        return reservationService.createReservation(
                request.slotId(),
                authenticatedUser.getId()
        );
    }
}