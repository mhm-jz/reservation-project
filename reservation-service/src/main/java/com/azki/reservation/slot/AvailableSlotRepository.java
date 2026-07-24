package com.azki.reservation.slot;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.azki.reservation.slot.dto.AvailableSlotResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface AvailableSlotRepository
        extends JpaRepository<AvailableSlotEntity, Long> {

    @Query("""
            select new com.azki.reservation.slot.dto.AvailableSlotResponse(
                    slot.id,
                    slot.startTime,
                    slot.endTime
            )
            from AvailableSlotEntity slot
            where slot.reserved = false
              and slot.startTime >= :from
              and slot.startTime < :to
            order by slot.startTime asc, slot.id asc
            """)
    List<AvailableSlotResponse> findAvailableSlots(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Query("""
            select new com.azki.reservation.slot.dto.AvailableSlotResponse(
                    slot.id,
                    slot.startTime,
                    slot.endTime
            )
            from AvailableSlotEntity slot
            where slot.reserved = false
              and slot.startTime >= :from
              and slot.startTime < :to
              and (
                    slot.startTime > :cursorStartTime
                    or (
                        slot.startTime = :cursorStartTime
                        and slot.id > :cursorId
                    )
              )
            order by slot.startTime asc, slot.id asc
            """)
    List<AvailableSlotResponse> findAvailableSlotsAfterCursor(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("cursorStartTime") LocalDateTime cursorStartTime,
            @Param("cursorId") Long cursorId,
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
