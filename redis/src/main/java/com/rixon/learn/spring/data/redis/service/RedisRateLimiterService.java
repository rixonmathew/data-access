package com.rixon.learn.spring.data.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisRateLimiterService {

    private final StringRedisTemplate redisTemplate;

    public boolean isAllowed(String key, int maxRequests, long windowSeconds) {
        String rateLimitKey = "rate_limit:" + key;
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000);

        // Remove old entries outside the sliding window
        redisTemplate.opsForZSet().removeRangeByScore(rateLimitKey, 0, windowStart);

        // Count current requests in window
        Long currentCount = redisTemplate.opsForZSet().zCard(rateLimitKey);

        if (currentCount != null && currentCount >= maxRequests) {
            return false;
        }

        // Add current request
        redisTemplate.opsForZSet().add(rateLimitKey, UUID.randomUUID().toString(), now);
        redisTemplate.expire(rateLimitKey, Duration.ofSeconds(windowSeconds * 2));
        return true;
    }
}
