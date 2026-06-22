/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration providing a {@link StringRedisTemplate} bean for
 * JWT blocklist management, refresh token storage, and rate limiting counters.
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a {@link StringRedisTemplate} using the auto-configured connection factory.
     *
     * <p>String-based operations are sufficient for JWT JTI keys and rate limit counters.
     *
     * @param connectionFactory the Lettuce connection factory auto-configured by Spring Boot.
     * @return configured {@link StringRedisTemplate}.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
