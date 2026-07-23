package com.azki.reservation.slot;

import com.azki.reservation.slot.dto.AvailableSlotResponse;
import com.azki.reservation.slot.dto.SlotCursor;
import com.azki.reservation.slot.dto.SlotPageResponse;
import com.azki.reservation.exception.InvalidSlotQueryException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final SlotDayCache slotDayCache;
    private final SlotQueryService slotQueryService;
    private final ObjectMapper objectMapper;

    public SlotPageResponse getAvailableSlots(
            LocalDateTime from,
            LocalDateTime to,
            int limit,
            String cursor
    ) {
        validateRange(from, to);
        validateLimit(limit);

        SlotCursor decodedCursor = decodeCursor(cursor);
        validateCursorRange(decodedCursor, from, to);
        List<AvailableSlotResponse> matchingSlots =
                collectMatchingSlots(from, to, decodedCursor, limit + 1);

        boolean hasNext = matchingSlots.size() > limit;
        List<AvailableSlotResponse> items = matchingSlots.subList(
                0,
                Math.min(limit, matchingSlots.size())
        );

        String nextCursor = hasNext
                ? encodeCursor(items.getLast())
                : null;

        return new SlotPageResponse(
                items,
                nextCursor,
                hasNext
        );
    }

    private List<AvailableSlotResponse> collectMatchingSlots(
            LocalDateTime from,
            LocalDateTime to,
            SlotCursor cursor,
            int targetSize
    ) {
        List<AvailableSlotResponse> matches = new ArrayList<>(targetSize);
        if (!slotDayCache.isEnabled()) {
            return slotQueryService.loadPage(
                    from,
                    to,
                    cursor,
                    targetSize
            );
        }

        LocalDate day = cursor == null
                ? from.toLocalDate()
                : cursor.startTime().toLocalDate();

        while (day.atStartOfDay().isBefore(to) &&
                matches.size() < targetSize) {
            SlotDayCache.DayCacheResult cacheResult =
                    slotDayCache.getDay(day);

            for (AvailableSlotResponse slot : cacheResult.slots()) {
                if (matchesRangeAndCursor(slot, from, to, cursor)) {
                    matches.add(slot);
                    if (matches.size() == targetSize) {
                        break;
                    }
                }
            }

            if (matches.size() == targetSize) {
                break;
            }

            if (cacheResult.fallbackRequired()) {
                matches.addAll(slotQueryService.loadPage(
                        laterOf(from, day.atStartOfDay()),
                        to,
                        cursor,
                        targetSize - matches.size()
                ));
                break;
            }

            day = day.plusDays(1);

            if (cacheResult.redisFailed() &&
                    day.atStartOfDay().isBefore(to)) {
                matches.addAll(slotQueryService.loadPage(
                        laterOf(from, day.atStartOfDay()),
                        to,
                        cursor,
                        targetSize - matches.size()
                ));
                break;
            }
        }
        return matches;
    }

    private LocalDateTime laterOf(
            LocalDateTime first,
            LocalDateTime second
    ) {
        return first.isAfter(second) ? first : second;
    }

    private boolean matchesRangeAndCursor(
            AvailableSlotResponse slot,
            LocalDateTime from,
            LocalDateTime to,
            SlotCursor cursor
    ) {
        if (slot.startTime().isBefore(from) ||
                !slot.startTime().isBefore(to)) {
            return false;
        }
        return cursor == null ||
                slot.startTime().isAfter(cursor.startTime()) ||
                (slot.startTime().isEqual(cursor.startTime()) &&
                        slot.id() > cursor.id());
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

    private void validateCursorRange(
            SlotCursor cursor,
            LocalDateTime from,
            LocalDateTime to
    ) {
        if (cursor != null &&
                (cursor.startTime().isBefore(from) ||
                        !cursor.startTime().isBefore(to))) {
            throw new InvalidSlotQueryException(
                    "Cursor startTime must be within the requested range"
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

    private String encodeCursor(AvailableSlotResponse slot) {
        try {
            byte[] cursorJson = objectMapper.writeValueAsBytes(
                    new SlotCursor(
                            slot.startTime(),
                            slot.id()
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

}
