package com.azki.reservation.reservation;

import com.azki.reservation.slot.AvailableSlotEntity;
import com.azki.reservation.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "reservations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reservations_slot_id",
                        columnNames = "slot_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(name = "slot_id", nullable = false)
    private AvailableSlotEntity slot;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    public ReservationEntity(
            UserEntity user,
            AvailableSlotEntity slot,
            LocalDateTime createdAt
    ) {
        this.user = user;
        this.slot = slot;
        this.createdAt = createdAt;
    }
}
