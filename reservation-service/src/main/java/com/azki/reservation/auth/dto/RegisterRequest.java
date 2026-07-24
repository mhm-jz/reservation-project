package com.azki.reservation.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterRequest(

        @NotBlank
        @Size(min = 3, max = 100)
        @Schema(example = "alice")
        String username,

        @NotBlank
        @Email
        @Size(max = 255)
        @Schema(example = "alice@example.com")
        String email,

        @NotBlank
        @Size(min = 8, max = 100)
        @Schema(example = "StrongPassword123")
        String password
) {

    public RegisterRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
