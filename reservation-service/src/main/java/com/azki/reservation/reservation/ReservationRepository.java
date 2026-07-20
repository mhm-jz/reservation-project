package com.azki.reservation.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationRepository
        extends JpaRepository<ReservationEntity, Long> {

    Optional<ReservationEntity> findByIdAndUser_Id(
            Long reservationId,
            Long userId
    );

    boolean existsBySlot_Id(Long slotId);
}