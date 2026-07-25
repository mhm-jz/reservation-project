package com.azki.reservation.slot;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "available_slots",
        indexes = {
                @Index(
                        name = "idx_available_slots_search",
                        columnList = "is_reserved, start_time, id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailableSlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "is_reserved", nullable = false)
    private boolean reserved;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    public AvailableSlotEntity(
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime createdAt
    ) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "Slot end time must be after start time"
            );
        }

        this.startTime = startTime;
        this.endTime = endTime;
        this.reserved = false;
        this.createdAt = createdAt;
    }
}
