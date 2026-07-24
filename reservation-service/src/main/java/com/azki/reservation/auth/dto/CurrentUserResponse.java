package com.azki.reservation.auth.dto;

public record CurrentUserResponse(
        Long id,
        String username,
        String email
) {
}
