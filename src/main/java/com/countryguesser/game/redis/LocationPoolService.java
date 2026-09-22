package com.countryguesser.game.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationPoolService {

    private static final String POOL_KEY = "location_pool";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void push(LocationEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForList().rightPush(POOL_KEY, json);
        } catch (JacksonException e) {
            log.error("Failed to serialize location entry: {}", entry, e);
        }
    }

    public long size() {
        Long size = redisTemplate.opsForList().size(POOL_KEY);
        return size != null ? size : 0;
    }

    public Optional<LocationEntry> pop() {
        String json = redisTemplate.opsForList().leftPop(POOL_KEY);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, LocationEntry.class));
        } catch (JacksonException e) {
            log.error("Failed to deserialize location entry: {}", json, e);
            return Optional.empty();
        }
    }
}