package com.rixon.learn.spring.data.redis.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class RedisDistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLockService.class);

    private final StringRedisTemplate redisTemplate;

    private static final String UNLOCK_LUA_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private final DefaultRedisScript<Long> unlockScript = new DefaultRedisScript<>(UNLOCK_LUA_SCRIPT, Long.class);

    public boolean acquireLock(String lockKey, String lockValue, long expireMillis) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofMillis(expireMillis));
        return Boolean.TRUE.equals(success);
    }

    public boolean releaseLock(String lockKey, String lockValue) {
        Long result = redisTemplate.execute(unlockScript, Collections.singletonList(lockKey), lockValue);
        return result != null && result == 1L;
    }
}
