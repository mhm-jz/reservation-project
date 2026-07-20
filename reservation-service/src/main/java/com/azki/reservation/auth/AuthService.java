package com.azki.reservation.auth;

import com.azki.reservation.auth.dto.AuthResponse;
import com.azki.reservation.auth.dto.LoginRequest;
import com.azki.reservation.auth.dto.RegisterRequest;
import com.azki.reservation.auth.dto.UserResponse;
import com.azki.reservation.exception.UsernameAlreadyExistsException;
import com.azki.reservation.security.AuthenticatedUser;
import com.azki.reservation.security.JwtService;
import com.azki.reservation.user.UserEntity;
import com.azki.reservation.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {

        String username = request.username().trim();

        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }

        String passwordHash = passwordEncoder.encode(
                request.password()
        );

        UserEntity user = new UserEntity(
                username,
                passwordHash
        );

        UserEntity savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getUsername()
        );
    }

    public AuthResponse login(LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.username().trim(),
                                request.password()
                        )
                );

        AuthenticatedUser authenticatedUser =
                (AuthenticatedUser) authentication.getPrincipal();

        String accessToken =
                jwtService.generateToken(authenticatedUser);

        return new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds()
        );
    }
}