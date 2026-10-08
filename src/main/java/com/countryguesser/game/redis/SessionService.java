package com.countryguesser.game.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_KEY_PREFIX = "active_sessions:";
    private static final Duration ROUND_DURATION = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public void startRound(Long userId, String panoId, String countryCode, int streak) {
        String key = SESSION_KEY_PREFIX + userId;

        redisTemplate.opsForHash().putAll(key, Map.of(
                "panoId", panoId,
                "countryCode", countryCode,
                "streak", String.valueOf(streak)
        ));
        redisTemplate.expire(key, ROUND_DURATION);
    }

    public boolean hasActiveRound(Long userId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForHash().hasKey(SESSION_KEY_PREFIX + userId, "panoId"));
    }

    public Optional<GameSession> getActiveRound(Long userId) {
        String key = SESSION_KEY_PREFIX + userId;
        Object panoId = redisTemplate.opsForHash().get(key, "panoId");

        if (panoId == null) {
            return Optional.empty();
        }

        String countryCode = (String) redisTemplate.opsForHash().get(key, "countryCode");
        String streak = (String) redisTemplate.opsForHash().get(key, "streak");

        return Optional.of(new GameSession((String) panoId, countryCode, Integer.parseInt(streak)));
    }

    public int getPendingStreak(Long userId) {
        Object streak = redisTemplate.opsForHash().get(SESSION_KEY_PREFIX + userId, "streak");
        return streak != null ? Integer.parseInt((String) streak) : 0;
    }

    public long getRemainingSeconds(Long userId) {
        Long ttl = redisTemplate.getExpire(SESSION_KEY_PREFIX + userId, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }

    public void markCorrectGuess(Long userId, int newStreak) {
        String key = SESSION_KEY_PREFIX + userId;

        redisTemplate.opsForHash().delete(key, "panoId", "countryCode");
        redisTemplate.opsForHash().put(key, "streak", String.valueOf(newStreak));
        redisTemplate.expire(key, ROUND_DURATION);
    }

    public void clearSession(Long userId) {
        redisTemplate.delete(SESSION_KEY_PREFIX + userId);
    }
}