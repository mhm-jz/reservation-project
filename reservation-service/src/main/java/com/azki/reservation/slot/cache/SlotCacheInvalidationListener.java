package com.azki.reservation.slot.cache;

import com.azki.reservation.slot.event.SlotAvailabilityChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SlotCacheInvalidationListener {

    private final SlotDayHeadCache slotDayHeadCache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(SlotAvailabilityChangedEvent event) {
        slotDayHeadCache.incrementVersion(event.day());
    }
}
