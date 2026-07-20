package com.azki.reservation.slot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailableSlotRepository
        extends JpaRepository<AvailableSlotEntity, Long> {
}