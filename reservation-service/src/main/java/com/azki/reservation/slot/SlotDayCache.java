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

    private final AvailableSlotRepository slotRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.slots-cache.enabled:true}")
    private boolean enabled;

    public List<AvailableSlotResponse> getDay(LocalDate day) {
        if (!enabled) {
            return loadFromMySql(day);
        }

        try {
            String version = getVersion(day);
            String dataKey = dataKey(day, version);
            String cached = redisTemplate.opsForValue().get(dataKey);
            if (cached != null) {
                return deserialize(cached);
            }
            return rebuildOrWait(day, version, dataKey);
        } catch (Exception ignored) {
            return loadFromMySql(day);
        }
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

    private List<AvailableSlotResponse> rebuildOrWait(
            LocalDate day,
            String version,
            String dataKey
    ) throws Exception {
        String lockKey = lockKey(day, version);
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockToken, LOCK_TTL);

        if (Boolean.TRUE.equals(acquired)) {
            try {
                List<AvailableSlotResponse> slots = loadFromMySql(day);
                redisTemplate.opsForValue().set(
                        dataKey,
                        objectMapper.writeValueAsString(slots),
                        cacheTtl()
                );
                return slots;
            } finally {
                releaseLock(lockKey, lockToken);
            }
        }

        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                Thread.sleep(50L * (attempt + 1));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            String cached = redisTemplate.opsForValue().get(dataKey);
            if (cached != null) {
                return deserialize(cached);
            }
        }
        return loadFromMySql(day);
    }

    private List<AvailableSlotResponse> loadFromMySql(LocalDate day) {
        return slotRepository.findAvailableSlotDtos(
                day.atStartOfDay(),
                day.plusDays(1).atStartOfDay()
        );
    }

    private String getVersion(LocalDate day) {
        String version = redisTemplate.opsForValue().get(versionKey(day));
        return version == null ? "0" : version;
    }

    private List<AvailableSlotResponse> deserialize(String json) throws Exception {
        return objectMapper.readValue(json, SLOT_LIST_TYPE);
    }

    private void releaseLock(String lockKey, String token) {
        try {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), token);
        } catch (Exception ignored) {
            // The lock expires quickly even if Redis becomes unavailable.
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
}
