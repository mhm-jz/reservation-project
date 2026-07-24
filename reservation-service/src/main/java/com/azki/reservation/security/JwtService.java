package com.azki.reservation.security;

import com.azki.reservation.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;

        byte[] keyBytes = Decoders.BASE64.decode(
                jwtProperties.secret()
        );

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(AuthenticatedUser user) {

        Instant now = Instant.now();

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

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public Claims extractValidatedClaims(String token) {
        return extractClaims(token);
    }

    public boolean isTokenValid(
            String token,
            UserDetails userDetails
    ) {
        Claims claims = extractClaims(token);

        return claims.getSubject()
                .equals(userDetails.getUsername());
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
