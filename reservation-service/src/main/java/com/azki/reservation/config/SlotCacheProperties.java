package com.azki.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.slots-cache")
public record SlotCacheProperties(
        boolean enabled,
        int headSize,
        Duration minimumTtl,
        Duration maximumTtl,
        Duration lockLease,
        List<Duration> lockRetryDelays
) {
}
