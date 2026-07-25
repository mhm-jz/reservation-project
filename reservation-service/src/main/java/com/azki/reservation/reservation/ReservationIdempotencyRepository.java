package com.azki.reservation.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReservationIdempotencyRepository
        extends JpaRepository<ReservationIdempotencyEntity, Long> {

    @Modifying
    @Query(
            value = """
                    insert ignore into reservation_idempotency (
                        user_id,
                        idempotency_key,
                        slot_id,
                        created_at
                    ) values (
                        :userId,
                        :idempotencyKey,
                        :slotId,
                        :createdAt
                    )
                    """,
            nativeQuery = true
    )
    int claim(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("slotId") Long slotId,
            @Param("createdAt") LocalDateTime createdAt
    );

    Optional<ReservationIdempotencyEntity>
    findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update ReservationIdempotencyEntity idempotency
            set idempotency.reservationId = :reservationId,
                idempotency.slotStartTime = :slotStartTime,
                idempotency.slotEndTime = :slotEndTime,
                idempotency.reservationCreatedAt = :reservationCreatedAt
            where idempotency.userId = :userId
              and idempotency.idempotencyKey = :idempotencyKey
              and idempotency.reservationId is null
            """)
    int complete(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("reservationId") Long reservationId,
            @Param("slotStartTime") LocalDateTime slotStartTime,
            @Param("slotEndTime") LocalDateTime slotEndTime,
            @Param("reservationCreatedAt")
            LocalDateTime reservationCreatedAt
    );
}
