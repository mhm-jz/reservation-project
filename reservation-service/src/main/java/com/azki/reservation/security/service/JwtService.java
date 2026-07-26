package com.azki.reservation.security.service;

import com.azki.reservation.config.JwtProperties;
import com.azki.reservation.security.model.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;
    private final Clock clock;

    public JwtService(
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;

        byte[] keyBytes = Decoders.BASE64.decode(
                jwtProperties.secret()
        );

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(AuthenticatedUser user) {

        Instant now = Instant.now(clock);

        Instant expiration = now.plus(
                jwtProperties.accessTokenExpiration()
        );

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    public Claims extractValidatedClaims(String token) {
        return extractClaims(token);
    }

    public long getExpirationSeconds() {
        return jwtProperties
                .accessTokenExpiration()
                .toSeconds();
    }

    private Claims extractClaims(String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
