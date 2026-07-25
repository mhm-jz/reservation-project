package com.azki.reservation.slot.service;

import com.azki.reservation.config.SlotCacheProperties;
import com.azki.reservation.slot.cache.SlotDayHeadCache;
import com.azki.reservation.config.SlotSearchProperties;
import com.azki.reservation.slot.dto.AvailableSlotResponse;
import com.azki.reservation.slot.dto.SlotCursor;
import com.azki.reservation.slot.dto.SlotPageResponse;
import com.azki.reservation.common.exception.InvalidSlotQueryException;
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

    private final SlotDayHeadCache slotDayHeadCache;
    private final SlotQueryService slotQueryService;
    private final ObjectMapper objectMapper;
    private final SlotSearchProperties searchProperties;
    private final SlotCacheProperties cacheProperties;

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
        List<AvailableSlotResponse> matchingSlots = loadMatchingSlots(
                from,
                to,
                decodedCursor,
                limit + 1
        );

        return createPage(matchingSlots, limit);
    }

    private List<AvailableSlotResponse> loadMatchingSlots(
            LocalDateTime from,
            LocalDateTime to,
            SlotCursor cursor,
            int targetSize
    ) {
        return cursor != null || !slotDayHeadCache.isEnabled()
                ? slotQueryService.loadPage(
                        from,
                        to,
                        cursor,
                        targetSize
                )
                : collectFirstPage(from, to, targetSize);
    }

    private SlotPageResponse createPage(
            List<AvailableSlotResponse> matchingSlots,
            int limit
    ) {
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

    private List<AvailableSlotResponse> collectFirstPage(
            LocalDateTime from,
            LocalDateTime to,
            int targetSize
    ) {
        List<AvailableSlotResponse> matches = new ArrayList<>(targetSize);
        LocalDate day = from.toLocalDate();

        while (day.atStartOfDay().isBefore(to) &&
                matches.size() < targetSize) {
            SlotDayHeadCache.DayHeadCacheResult cacheResult =
                    slotDayHeadCache.getDayHead(day);

            if (cacheResult.fallbackRequired() ||
                    cacheResult.redisFailed()) {
                return slotQueryService.loadPage(
                        from,
                        to,
                        null,
                        targetSize
                );
            }

            for (AvailableSlotResponse slot : cacheResult.slots()) {
                if (matchesRange(slot, from, to)) {
                    matches.add(slot);
                    if (matches.size() == targetSize) {
                        break;
                    }
                }
            }

            if (matches.size() == targetSize) {
                break;
            }

            if (cacheResult.slots().size() ==
                    cacheProperties.headSize()) {
                return slotQueryService.loadPage(
                        from,
                        to,
                        null,
                        targetSize
                );
            }

            day = day.plusDays(1);
        }
        return matches;
    }

    private boolean matchesRange(
            AvailableSlotResponse slot,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return !slot.startTime().isBefore(from) &&
                slot.startTime().isBefore(to);
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
                searchProperties.maximumRange()
        ) > 0) {
            throw new InvalidSlotQueryException(
                    "The requested range must not exceed " +
                            searchProperties.maximumRange().toDays() +
                            " days"
            );
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1) {
            throw new InvalidSlotQueryException(
                    "'limit' must be between 1 and " +
                            searchProperties.maximumPageSize()
            );
        }
        if (limit > searchProperties.maximumPageSize()) {
            throw new InvalidSlotQueryException(
                    "getAvailableSlots.limit: must be less than or equal to " +
                            searchProperties.maximumPageSize()
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
