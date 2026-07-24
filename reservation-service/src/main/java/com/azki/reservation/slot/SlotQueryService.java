package com.azki.reservation.slot;

import com.azki.reservation.slot.dto.AvailableSlotResponse;
import com.azki.reservation.slot.dto.SlotCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotQueryService {

    private final AvailableSlotRepository slotRepository;

    @Transactional(
            readOnly = true,
            propagation = Propagation.REQUIRES_NEW
    )
    public List<AvailableSlotResponse> loadDayHead(
            LocalDate day,
            int limit
    ) {
        return slotRepository.findAvailableSlots(
                day.atStartOfDay(),
                day.plusDays(1).atStartOfDay(),
                PageRequest.of(0, limit)
        );
    }

    @Transactional(
            readOnly = true,
            propagation = Propagation.REQUIRES_NEW
    )
    public List<AvailableSlotResponse> loadPage(
            LocalDateTime from,
            LocalDateTime to,
            SlotCursor cursor,
            int limit
    ) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        return cursor == null
                ? slotRepository.findAvailableSlots(from, to, pageRequest)
                : slotRepository.findAvailableSlotsAfterCursor(
                        from,
                        to,
                        cursor.startTime(),
                        cursor.id(),
                        pageRequest
                );
    }
}
