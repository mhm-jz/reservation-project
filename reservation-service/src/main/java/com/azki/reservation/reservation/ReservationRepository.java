package com.azki.reservation.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationRepository
        extends JpaRepository<ReservationEntity, Long> {

    boolean existsBySlot_Id(Long slotId);

    @Query("""
            select reservation.slot.id
            from ReservationEntity reservation
            where reservation.id = :reservationId
              and reservation.user.id = :userId
            """)
    Optional<Long> findSlotIdByReservationIdAndUserId(
            @Param("reservationId") Long reservationId,
            @Param("userId") Long userId
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            delete from ReservationEntity reservation
            where reservation.id = :reservationId
              and reservation.user.id = :userId
            """)
    int deleteByReservationIdAndUserId(
            @Param("reservationId") Long reservationId,
            @Param("userId") Long userId
    );
}