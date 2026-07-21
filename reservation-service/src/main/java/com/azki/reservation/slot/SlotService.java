package com.azki.reservation.slot;

import com.azki.reservation.slot.dto.AvailableSlotResponse;
import com.azki.reservation.slot.dto.SlotPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.azki.reservation.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final AvailableSlotRepository slotRepository;
    @Cacheable(
            cacheNames = CacheConfig.AVAILABLE_SLOTS_CACHE,
            key = "#root.args[0] + ':' + #root.args[1]"
    )
    @Transactional(readOnly = true)
    public SlotPageResponse getAvailableSlots(
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size);

        Slice<AvailableSlotEntity> slotSlice =
                slotRepository.findAvailableSlots(
                        LocalDateTime.now(),
                        pageable
                );

        List<AvailableSlotResponse> items =
                slotSlice.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return new SlotPageResponse(
                items,
                slotSlice.getNumber(),
                slotSlice.getSize(),
                slotSlice.hasNext()
        );
    }

    private AvailableSlotResponse toResponse(
            AvailableSlotEntity slot
    ) {
        return new AvailableSlotResponse(
                slot.getId(),
                slot.getStartTime(),
                slot.getEndTime()
        );
    }
}