package com.azki.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.performance-seeding")
public record PerformanceSeedingProperties(
        boolean enabled,
        String userPassword,
        int batchSize
) {
}
