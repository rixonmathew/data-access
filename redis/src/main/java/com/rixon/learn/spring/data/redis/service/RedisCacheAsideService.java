package com.rixon.learn.spring.data.redis.service;

import com.rixon.model.account.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RedisCacheAsideService {

    private final RedisTemplate<String, Object> redisTemplate;

    public Account getAccount(String accountNumber, Supplier<Account> dbFallback, Duration ttl) {
        String key = "cache:account:" + accountNumber;
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached instanceof Account account) {
            return account;
        }

        Account accountFromDb = dbFallback.get();
        if (accountFromDb != null) {
            redisTemplate.opsForValue().set(key, accountFromDb, ttl);
        }
        return accountFromDb;
    }

    public void evictAccount(String accountNumber) {
        String key = "cache:account:" + accountNumber;
        redisTemplate.delete(key);
    }
}
