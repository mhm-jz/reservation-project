package com.azki.reservation.auth;

import com.azki.reservation.common.openapi.OpenApiConfig;
import com.azki.reservation.auth.dto.AuthResponse;
import com.azki.reservation.auth.dto.CurrentUserResponse;
import com.azki.reservation.auth.dto.LoginRequest;
import com.azki.reservation.auth.dto.RegisterRequest;
import com.azki.reservation.auth.dto.UserResponse;
import com.azki.reservation.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(
        name = "Authentication",
        description = "Public registration/login and authenticated identity"
)
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Register a new user")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "400",
                    ref = "#/components/responses/RegistrationBadRequest"
            ),
            @ApiResponse(
                    responseCode = "409",
                    ref = "#/components/responses/RegistrationConflict"
            )
    })
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Login",
            description = "Authenticates a user and returns a JWT access token."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "400",
                    ref = "#/components/responses/LoginBadRequest"
            ),
            @ApiResponse(
                    responseCode = "401",
                    ref = "#/components/responses/InvalidCredentials"
            )
    })
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get the current user",
            description = "Returns the authenticated user's identity."
    )
    public CurrentUserResponse currentUser(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser
    ) {
        return authService.currentUser(authenticatedUser.getId());
    }
}
