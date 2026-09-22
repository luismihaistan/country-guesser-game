package com.countryguesser.game.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PanoDeduplicationService {

    private static final String USED_PANO_IDS_KEY = "used_pano_ids";

    private final StringRedisTemplate redisTemplate;

    public boolean isAlreadyUsed(String panoId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(USED_PANO_IDS_KEY, panoId));
    }

    public void markAsUsed(String panoId) {
        redisTemplate.opsForSet().add(USED_PANO_IDS_KEY, panoId);
    }
}