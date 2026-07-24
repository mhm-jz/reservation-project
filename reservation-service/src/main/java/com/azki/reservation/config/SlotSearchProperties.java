package com.azki.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.slot-search")
public record SlotSearchProperties(
        Duration maximumRange,
        int defaultPageSize,
        int maximumPageSize
) {
}
