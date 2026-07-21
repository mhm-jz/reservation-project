package com.azki.reservation.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String AVAILABLE_SLOTS_CACHE =
            "available-slots";

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {

        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer()
                        .configure(objectMapper -> {
                            objectMapper.registerModule(
                                    new JavaTimeModule()
                            );

                            objectMapper.disable(
                                    SerializationFeature
                                            .WRITE_DATES_AS_TIMESTAMPS
                            );
                        });

        return RedisCacheConfiguration
                .defaultCacheConfig()

                .entryTtl(Duration.ofSeconds(30))
                .disableCachingNullValues()

                .computePrefixWith(cacheName ->
                        "reservation-platform::"
                                + cacheName
                                + "::"
                )

                .serializeKeysWith(
                        RedisSerializationContext
                                .SerializationPair
                                .fromSerializer(
                                        new StringRedisSerializer()
                                )
                )

                .serializeValuesWith(
                        RedisSerializationContext
                                .SerializationPair
                                .fromSerializer(valueSerializer)
                );
    }

}