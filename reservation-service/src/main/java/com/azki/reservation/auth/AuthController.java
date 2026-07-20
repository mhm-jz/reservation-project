package com.azki.reservation.auth;

import com.azki.reservation.auth.dto.AuthResponse;
import com.azki.reservation.auth.dto.CurrentUserResponse;
import com.azki.reservation.auth.dto.LoginRequest;
import com.azki.reservation.auth.dto.RegisterRequest;
import com.azki.reservation.auth.dto.UserResponse;
import com.azki.reservation.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(
            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser
    ) {
        return new CurrentUserResponse(
                authenticatedUser.getId(),
                authenticatedUser.getUsername()
        );
    }
}