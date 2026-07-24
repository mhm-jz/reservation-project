package com.azki.reservation.auth;

import com.azki.reservation.auth.dto.AuthResponse;
import com.azki.reservation.auth.dto.CurrentUserResponse;
import com.azki.reservation.auth.dto.LoginRequest;
import com.azki.reservation.auth.dto.RegisterRequest;
import com.azki.reservation.auth.dto.UserResponse;
import com.azki.reservation.auth.mapper.UserMapper;
import com.azki.reservation.common.exception.AuthenticatedUserNotFoundException;
import com.azki.reservation.common.exception.EmailAlreadyExistsException;
import com.azki.reservation.common.exception.UsernameAlreadyExistsException;
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

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        validateUserUniqueness(username, email);

        UserEntity user = createUser(
                username,
                email,
                request.password()
        );
        UserEntity savedUser = userRepository.save(user);

        return userMapper.toUserResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        UserEntity user = loadUser(userId);
        return userMapper.toCurrentUserResponse(user);
    }

    public AuthResponse login(LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                normalizeUsername(request.username()),
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

    private String normalizeUsername(String username) {
        return username.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateUserUniqueness(
            String username,
            String email
    ) {
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException();
        }
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }
    }

    private UserEntity createUser(
            String username,
            String email,
            String rawPassword
    ) {
        String passwordHash = passwordEncoder.encode(rawPassword);
        return new UserEntity(username, email, passwordHash);
    }

    private UserEntity loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }
}
