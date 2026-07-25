package com.azki.reservation.reservation;

import com.azki.reservation.reservation.dto.ReservationResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "reservation_idempotency",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reservation_idempotency_user_key",
                        columnNames = {
                                "user_id",
                                "idempotency_key"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationIdempotencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 36
    )
    private String idempotencyKey;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "slot_start_time")
    private LocalDateTime slotStartTime;

    @Column(name = "slot_end_time")
    private LocalDateTime slotEndTime;

    @Column(name = "reservation_created_at")
    private LocalDateTime reservationCreatedAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    public boolean hasSlotId(Long requestedSlotId) {
        return slotId.equals(requestedSlotId);
    }

    public ReservationResponse toResponse() {
        if (reservationId == null ||
                slotStartTime == null ||
                slotEndTime == null ||
                reservationCreatedAt == null) {
            throw new IllegalStateException(
                    "Reservation idempotency result is incomplete"
            );
        }

        return new ReservationResponse(
                reservationId,
                slotId,
                userId,
                slotStartTime,
                slotEndTime,
                reservationCreatedAt
        );
    }
}
