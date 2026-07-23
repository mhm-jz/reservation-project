package com.azki.reservation.slot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SlotCacheInvalidationListener {

    private final SlotDayCache slotDayCache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(SlotAvailabilityChangedEvent event) {
        slotDayCache.incrementVersion(event.day());
    }
}
