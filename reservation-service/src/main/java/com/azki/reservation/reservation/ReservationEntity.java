package com.azki.reservation.reservation;

import com.azki.reservation.slot.AvailableSlotEntity;
import com.azki.reservation.user.UserEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "reservations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reservations_slot_id",
                        columnNames = "slot_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_reservations_user_created_at",
                        columnList = "user_id, created_at"
                )
        }
)
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private UserEntity user;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "slot_id",
            nullable = false
    )
    private AvailableSlotEntity slot;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    protected ReservationEntity() {
    }

    public ReservationEntity(
            UserEntity user,
            AvailableSlotEntity slot
    ) {
        this.user = user;
        this.slot = slot;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public AvailableSlotEntity getSlot() {
        return slot;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}