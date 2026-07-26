package com.azki.reservation.reservation.repository;

import com.azki.reservation.reservation.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationRepository
        extends JpaRepository<ReservationEntity, Long> {

    @Query("""
            select new com.azki.reservation.reservation.repository.OwnedReservationSlot(
                    reservation.slot.id,
                    reservation.slot.startTime
            )
            from ReservationEntity reservation
            where reservation.id = :reservationId
              and reservation.user.id = :userId
            """)
    Optional<OwnedReservationSlot> findOwnedSlotByReservationIdAndUserId(
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
