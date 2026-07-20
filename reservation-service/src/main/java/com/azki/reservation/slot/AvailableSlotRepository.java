package com.azki.reservation.slot;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
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
}