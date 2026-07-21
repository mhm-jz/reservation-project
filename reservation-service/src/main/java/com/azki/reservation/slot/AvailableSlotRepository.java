package com.azki.reservation.slot;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AvailableSlotRepository
        extends JpaRepository<AvailableSlotEntity, Long> {

    @Query("""
            select slot
            from AvailableSlotEntity slot
            where slot.reserved = false
              and slot.startTime >= :from
            order by slot.startTime asc, slot.id asc
            """)
    Slice<AvailableSlotEntity> findAvailableSlots(
            @Param("from") LocalDateTime from,
            Pageable pageable
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            update AvailableSlotEntity slot
            set slot.reserved = true
            where slot.id = :slotId
              and slot.reserved = false
              and slot.startTime >= :now
            """)
    int reserveIfAvailable(
            @Param("slotId") Long slotId,
            @Param("now") LocalDateTime now
    );


    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
        update AvailableSlotEntity slot
        set slot.reserved = false
        where slot.id = :slotId
          and slot.reserved = true
        """)
    int releaseReservedSlot(
            @Param("slotId") Long slotId
    );
}