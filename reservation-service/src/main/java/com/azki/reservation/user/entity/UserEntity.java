package com.azki.reservation.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = UserEntity.USERNAME_UNIQUE_CONSTRAINT,
                        columnNames = "username"
                ),
                @UniqueConstraint(
                        name = UserEntity.EMAIL_UNIQUE_CONSTRAINT,
                        columnNames = "email"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    public static final String USERNAME_UNIQUE_CONSTRAINT =
            "uk_users_username";
    public static final String EMAIL_UNIQUE_CONSTRAINT =
            "uk_users_email";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "username",
            nullable = false,
            length = 100
    )
    private String username;


    @Column(
            name = "email",
            nullable = false
    )
    private String email;


    @Column(
            name = "password",
            nullable = false
    )
    private String passwordHash;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    public UserEntity(
            String username,
            String email,
            String passwordHash,
            LocalDateTime createdAt
    ) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }
}
