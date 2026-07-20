package com.azki.reservation.slot;

import jakarta.persistence.*;

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
public class AvailableSlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "start_time",
            nullable = false
    )
    private LocalDateTime startTime;

    @Column(
            name = "end_time",
            nullable = false
    )
    private LocalDateTime endTime;

    @Column(
            name = "is_reserved",
            nullable = false
    )
    private boolean reserved;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    protected AvailableSlotEntity() {
    }

    public AvailableSlotEntity(
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
            throw new IllegalArgumentException(
                    "Slot end time must be after start time"
            );
        }

        this.startTime = startTime;
        this.endTime = endTime;
        this.reserved = false;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public boolean isReserved() {
        return reserved;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}