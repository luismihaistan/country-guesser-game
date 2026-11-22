package com.countryguesser.game.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class SessionService {

    private static final String SESSION_KEY_PREFIX = "active_sessions:";
    private static final String EXPIRATIONS_KEY = "session_expirations";
    private static final Duration SAFETY_TTL = Duration.ofHours(24);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CLAIM_EXPIRED_SCRIPT = createClaimExpiredScript();

    private final StringRedisTemplate redisTemplate;
    private final Duration roundDuration;

    public SessionService(
            StringRedisTemplate redisTemplate,
            @Value("${app.game.round-duration-seconds:600}") long roundDurationSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.roundDuration = Duration.ofSeconds(roundDurationSeconds);
    }

    public void startRound(Long userId, String panoId, String countryCode, int streak) {
        String key = SESSION_KEY_PREFIX + userId;
        long expiresAt = Instant.now().plus(roundDuration).toEpochMilli();

        redisTemplate.opsForHash().putAll(key, Map.of(
                "panoId", panoId,
                "countryCode", countryCode,
                "streak", String.valueOf(streak),
                "expiresAt", String.valueOf(expiresAt)
        ));
        redisTemplate.expire(key, SAFETY_TTL);
        redisTemplate.opsForZSet().add(EXPIRATIONS_KEY, userId.toString(), expiresAt);
    }

    public boolean hasActiveRound(Long userId) {
        return getActiveRound(userId).isPresent();
    }

    public Optional<GameSession> getActiveRound(Long userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(SESSION_KEY_PREFIX + userId);
        if (entries.isEmpty() || isExpired(entries, System.currentTimeMillis())) {
            return Optional.empty();
        }

        Object panoId = entries.get("panoId");
        Object countryCode = entries.get("countryCode");
        Object streak = entries.get("streak");
        if (panoId == null || countryCode == null || streak == null) {
            return Optional.empty();
        }

        return Optional.of(new GameSession(
                panoId.toString(),
                countryCode.toString(),
                Integer.parseInt(streak.toString())
        ));
    }

    public int getPendingStreak(Long userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(SESSION_KEY_PREFIX + userId);
        if (entries.isEmpty() || isExpired(entries, System.currentTimeMillis())) {
            return 0;
        }
        Object streak = entries.get("streak");
        return streak != null ? Integer.parseInt(streak.toString()) : 0;
    }

    public long getRemainingSeconds(Long userId) {
        String key = SESSION_KEY_PREFIX + userId;
        Object expiresAtObj = redisTemplate.opsForHash().get(key, "expiresAt");
        if (expiresAtObj != null) {
            long remainingMs = Long.parseLong(expiresAtObj.toString()) - System.currentTimeMillis();
            return remainingMs > 0 ? (remainingMs + 999) / 1000 : 0;
        }

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }

    public void markCorrectGuess(Long userId, int newStreak) {
        String key = SESSION_KEY_PREFIX + userId;
        long expiresAt = Instant.now().plus(roundDuration).toEpochMilli();

        redisTemplate.opsForHash().delete(key, "panoId", "countryCode");
        redisTemplate.opsForHash().putAll(key, Map.of(
                "streak", String.valueOf(newStreak),
                "expiresAt", String.valueOf(expiresAt)
        ));
        redisTemplate.expire(key, SAFETY_TTL);
        redisTemplate.opsForZSet().add(EXPIRATIONS_KEY, userId.toString(), expiresAt);
    }

    public void clearSession(Long userId) {
        redisTemplate.delete(SESSION_KEY_PREFIX + userId);
        redisTemplate.opsForZSet().remove(EXPIRATIONS_KEY, userId.toString());
    }

    public Optional<GameSession> claimExpiredSession(Long userId) {
        List<?> raw = redisTemplate.execute(
                CLAIM_EXPIRED_SCRIPT,
                List.of(SESSION_KEY_PREFIX + userId, EXPIRATIONS_KEY),
                userId.toString(),
                String.valueOf(System.currentTimeMillis())
        );

        if (raw == null || raw.size() < 2) {
            return Optional.empty();
        }

        Map<String, String> fields = new HashMap<>();
        for (int i = 0; i + 1 < raw.size(); i += 2) {
            fields.put(String.valueOf(raw.get(i)), String.valueOf(raw.get(i + 1)));
        }

        String streakStr = fields.get("streak");
        int streak = streakStr != null ? Integer.parseInt(streakStr) : 0;
        return Optional.of(new GameSession(fields.get("panoId"), fields.get("countryCode"), streak));
    }

    public List<Long> findExpiredUserIds(long cutoffEpochMs) {
        Set<String> members = redisTemplate.opsForZSet()
                .rangeByScore(EXPIRATIONS_KEY, Double.NEGATIVE_INFINITY, cutoffEpochMs);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().map(Long::parseLong).toList();
    }

    private boolean isExpired(Map<Object, Object> entries, long nowMs) {
        Object expiresAtObj = entries.get("expiresAt");
        if (expiresAtObj == null) {
            return false;
        }
        return nowMs >= Long.parseLong(expiresAtObj.toString());
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> createClaimExpiredScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        script.setScriptText("""
                local key = KEYS[1]
                local zset = KEYS[2]
                local userId = ARGV[1]
                local now = tonumber(ARGV[2])
                if redis.call('EXISTS', key) == 0 then
                    redis.call('ZREM', zset, userId)
                    return {}
                end
                local expiresAt = tonumber(redis.call('HGET', key, 'expiresAt'))
                if not expiresAt or now < expiresAt then
                    return {}
                end
                local data = redis.call('HGETALL', key)
                redis.call('DEL', key)
                redis.call('ZREM', zset, userId)
                return data
                """);
        return script;
    }
}