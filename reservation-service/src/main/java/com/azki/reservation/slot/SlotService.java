package com.azki.reservation.slot;

import com.azki.reservation.slot.dto.AvailableSlotResponse;
import com.azki.reservation.slot.dto.SlotCursor;
import com.azki.reservation.slot.dto.SlotPageResponse;
import com.azki.reservation.exception.InvalidSlotQueryException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.azki.reservation.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final AvailableSlotRepository slotRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(
            cacheNames = CacheConfig.AVAILABLE_SLOTS_CACHE,
            key = "#root.args[0] + ':' + #root.args[1] + ':' + #root.args[2] + ':' + #root.args[3]"
    )
    @Transactional(readOnly = true)
    public SlotPageResponse getAvailableSlots(
            LocalDateTime from,
            LocalDateTime to,
            int limit,
            String cursor
    ) {
        validateRange(from, to);
        validateLimit(limit);

        SlotCursor decodedCursor = decodeCursor(cursor);
        PageRequest pageRequest = PageRequest.of(0, limit + 1);

        List<AvailableSlotEntity> slots =
                decodedCursor == null
                        ? slotRepository.findAvailableSlots(
                                from,
                                to,
                                pageRequest
                        )
                        : slotRepository.findAvailableSlotsAfterCursor(
                                from,
                                to,
                                decodedCursor.startTime(),
                                decodedCursor.id(),
                                pageRequest
                        );

        boolean hasNext = slots.size() > limit;
        List<AvailableSlotEntity> returnedSlots =
                slots.subList(0, Math.min(limit, slots.size()));

        List<AvailableSlotResponse> items = returnedSlots
                        .stream()
                        .map(this::toResponse)
                        .toList();

        String nextCursor = hasNext
                ? encodeCursor(returnedSlots.getLast())
                : null;

        return new SlotPageResponse(
                items,
                nextCursor,
                hasNext
        );
    }

    private void validateRange(
            LocalDateTime from,
            LocalDateTime to
    ) {
        if (!to.isAfter(from)) {
            throw new InvalidSlotQueryException(
                    "'to' must be after 'from'"
            );
        }

        if (Duration.between(from, to).compareTo(
                Duration.ofDays(30)
        ) > 0) {
            throw new InvalidSlotQueryException(
                    "The requested range must not exceed 30 days"
            );
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new InvalidSlotQueryException(
                    "'limit' must be between 1 and 100"
            );
        }
    }

    private SlotCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            SlotCursor slotCursor = objectMapper.readValue(
                    decoded,
                    SlotCursor.class
            );

            if (slotCursor.startTime() == null ||
                    slotCursor.id() == null ||
                    slotCursor.id() <= 0) {
                throw new InvalidSlotQueryException(
                        "Cursor is invalid"
                );
            }

            return slotCursor;
        } catch (IllegalArgumentException | IOException exception) {
            throw new InvalidSlotQueryException(
                    "Cursor is invalid",
                    exception
            );
        }
    }

    private String encodeCursor(AvailableSlotEntity slot) {
        try {
            byte[] cursorJson = objectMapper.writeValueAsBytes(
                    new SlotCursor(
                            slot.getStartTime(),
                            slot.getId()
                    )
            );

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(cursorJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not create slot cursor",
                    exception
            );
        }
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
