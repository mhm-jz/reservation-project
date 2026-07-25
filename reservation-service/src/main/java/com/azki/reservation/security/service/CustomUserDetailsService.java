package com.azki.reservation.security.service;

import com.azki.reservation.security.model.AuthenticatedUser;
import com.azki.reservation.user.entity.UserEntity;
import com.azki.reservation.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {

        UserEntity user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found"
                        )
                );

        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash()
        );
    }
}
