package com.azki.reservation.slot;

import com.azki.reservation.slot.dto.AvailableSlotResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
public class SlotDayCache {

    private static final TypeReference<List<AvailableSlotResponse>> SLOT_LIST_TYPE =
            new TypeReference<>() {};
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    private final SlotQueryService slotQueryService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.slots-cache.enabled:true}")
    private boolean enabled;

    public DayCacheResult getDay(LocalDate day) {
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
                        return DayCacheResult.redisFailure();
                    }
                    return rebuildOrWait(day, version, dataKey);
                }

                if (version.equals(getVersion(day))) {
                    return DayCacheResult.available(slots);
                }
                return DayCacheResult.fallback();
            }
            return rebuildOrWait(day, version, dataKey);
        } catch (Exception ignored) {
            return DayCacheResult.redisFailure();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void incrementVersion(LocalDate day) {
        if (!enabled) {
            return;
        }

        try {
            redisTemplate.opsForValue().increment(versionKey(day));
        } catch (Exception ignored) {
            // Redis is an optional acceleration layer.
        }
    }

    private DayCacheResult rebuildOrWait(
            LocalDate day,
            String version,
            String dataKey
    ) throws Exception {
        String lockKey = lockKey(day, version);
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, LOCK_TTL);

        if (Boolean.TRUE.equals(acquired)) {
            List<AvailableSlotResponse> slots;
            try {
                slots = slotQueryService.loadDay(day);
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
                    ? DayCacheResult.available(slots)
                    : DayCacheResult.loadedWithRedisFailure(slots);
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Thread.sleep(attempt == 0 ? 25L : 50L);
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
                            ? DayCacheResult.fallback()
                            : DayCacheResult.redisFailure();
                }
                if (version.equals(getVersion(day))) {
                    return DayCacheResult.available(slots);
                }
                return DayCacheResult.fallback();
            }
        }
        return DayCacheResult.fallback();
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
        return Duration.ofSeconds(ThreadLocalRandom.current().nextLong(30, 61));
    }

    private String versionKey(LocalDate day) {
        return "slots:version:" + day;
    }

    private String dataKey(LocalDate day, String version) {
        return "slots:data:" + day + ":v" + version;
    }

    private String lockKey(LocalDate day, String version) {
        return "slots:lock:" + day + ":v" + version;
    }

    public record DayCacheResult(
            List<AvailableSlotResponse> slots,
            boolean fallbackRequired,
            boolean redisFailed
    ) {
        private static DayCacheResult available(
                List<AvailableSlotResponse> slots
        ) {
            return new DayCacheResult(slots, false, false);
        }

        private static DayCacheResult loadedWithRedisFailure(
                List<AvailableSlotResponse> slots
        ) {
            return new DayCacheResult(slots, false, true);
        }

        private static DayCacheResult fallback() {
            return new DayCacheResult(List.of(), true, false);
        }

        private static DayCacheResult redisFailure() {
            return new DayCacheResult(List.of(), true, true);
        }
    }
}
