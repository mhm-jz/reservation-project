package com.azki.reservation.slot;

import com.azki.reservation.config.SlotCacheProperties;
import com.azki.reservation.slot.dto.AvailableSlotResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class SlotDayHeadCache {

    private static final TypeReference<List<AvailableSlotResponse>> SLOT_LIST_TYPE =
            new TypeReference<>() {};
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    private final SlotQueryService slotQueryService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SlotCacheProperties properties;

    public DayHeadCacheResult getDayHead(LocalDate day) {
        try {
            String version = getVersion(day);
            String dataKey = dataKey(day, version);
            String cached = redisTemplate.opsForValue().get(dataKey);
            if (cached != null) {
                List<AvailableSlotResponse> slots;
                try {
                    slots = deserialize(cached);
                } catch (Exception exception) {
                    if (!deleteCorruptEntry(dataKey)) {
                        return DayHeadCacheResult.redisFailure();
                    }
                    return rebuildOrWait(day, version, dataKey);
                }

                if (version.equals(getVersion(day))) {
                    return DayHeadCacheResult.available(slots);
                }
                return DayHeadCacheResult.fallback();
            }
            return rebuildOrWait(day, version, dataKey);
        } catch (Exception ignored) {
            return DayHeadCacheResult.redisFailure();
        }
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public void incrementVersion(LocalDate day) {
        if (!properties.enabled()) {
            return;
        }

        try {
            redisTemplate.opsForValue().increment(versionKey(day));
        } catch (Exception ignored) {
            // Redis is an optional acceleration layer.
        }
    }

    private DayHeadCacheResult rebuildOrWait(
            LocalDate day,
            String version,
            String dataKey
    ) throws Exception {
        String lockKey = lockKey(day, version);
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(
                        lockKey,
                        lockToken,
                        properties.lockLease()
                );

        if (Boolean.TRUE.equals(acquired)) {
            List<AvailableSlotResponse> slots;
            try {
                slots = slotQueryService.loadDayHead(
                        day,
                        properties.headSize()
                );
            } catch (RuntimeException exception) {
                releaseLock(lockKey, lockToken);
                throw exception;
            }

            boolean redisAvailable = true;
            try {
                redisTemplate.opsForValue().set(
                        dataKey,
                        objectMapper.writeValueAsString(slots),
                        cacheTtl()
                );
            } catch (Exception ignored) {
                redisAvailable = false;
            }
            redisAvailable &= releaseLock(lockKey, lockToken);

            return redisAvailable
                    ? DayHeadCacheResult.available(slots)
                    : DayHeadCacheResult.loadedWithRedisFailure(slots);
        }

        for (Duration retryDelay : properties.lockRetryDelays()) {
            try {
                Thread.sleep(retryDelay.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            String cached = redisTemplate.opsForValue().get(dataKey);
            if (cached != null) {
                List<AvailableSlotResponse> slots;
                try {
                    slots = deserialize(cached);
                } catch (Exception exception) {
                    return deleteCorruptEntry(dataKey)
                            ? DayHeadCacheResult.fallback()
                            : DayHeadCacheResult.redisFailure();
                }
                if (version.equals(getVersion(day))) {
                    return DayHeadCacheResult.available(slots);
                }
                return DayHeadCacheResult.fallback();
            }
        }
        return DayHeadCacheResult.fallback();
    }

    private String getVersion(LocalDate day) {
        String version = redisTemplate.opsForValue().get(versionKey(day));
        return version == null ? "0" : version;
    }

    private List<AvailableSlotResponse> deserialize(String json) throws Exception {
        return objectMapper.readValue(json, SLOT_LIST_TYPE);
    }

    private boolean releaseLock(String lockKey, String token) {
        try {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), token);
            return true;
        } catch (Exception ignored) {
            // The lock expires quickly even if Redis becomes unavailable.
            return false;
        }
    }

    private boolean deleteCorruptEntry(String dataKey) {
        try {
            redisTemplate.delete(dataKey);
            return true;
        } catch (Exception ignored) {
            // A corrupt entry will expire even if deletion fails.
            return false;
        }
    }

    private Duration cacheTtl() {
        long minimumSeconds = properties.minimumTtl().toSeconds();
        long maximumSeconds = properties.maximumTtl().toSeconds();
        return Duration.ofSeconds(
                ThreadLocalRandom.current().nextLong(
                        minimumSeconds,
                        maximumSeconds + 1
                )
        );
    }

    private String versionKey(LocalDate day) {
        return "slots:version:" + day;
    }

    private String dataKey(LocalDate day, String version) {
        return "slots:head:" + day + ":v" + version;
    }

    private String lockKey(LocalDate day, String version) {
        return "slots:head-lock:" + day + ":v" + version;
    }

    public record DayHeadCacheResult(
            List<AvailableSlotResponse> slots,
            boolean fallbackRequired,
            boolean redisFailed
    ) {
        private static DayHeadCacheResult available(
                List<AvailableSlotResponse> slots
        ) {
            return new DayHeadCacheResult(slots, false, false);
        }

        private static DayHeadCacheResult loadedWithRedisFailure(
                List<AvailableSlotResponse> slots
        ) {
            return new DayHeadCacheResult(slots, false, true);
        }

        private static DayHeadCacheResult fallback() {
            return new DayHeadCacheResult(List.of(), true, false);
        }

        private static DayHeadCacheResult redisFailure() {
            return new DayHeadCacheResult(List.of(), true, true);
        }
    }
}
