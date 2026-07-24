package com.azki.reservation.auth.dto;

public record UserResponse(
        Long id,
        String username,
        String email
) {
}
