package com.countryguesser.game.scheduler;

import com.countryguesser.game.game.GameService;
import com.countryguesser.game.redis.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionExpirationJob {

    // Short grace period so an active browser tab can receive the country reveal on timeout.
    private static final long GRACE_PERIOD_MS = 5_000;

    private final SessionService sessionService;
    private final GameService gameService;

    @Scheduled(fixedDelay = 10 * 1000)
    public void sweepExpiredSessions() {
        long cutoff = System.currentTimeMillis() - GRACE_PERIOD_MS;
        List<Long> expiredUserIds = sessionService.findExpiredUserIds(cutoff);

        for (Long userId : expiredUserIds) {
            try {
                gameService.expireSessionIfNeeded(userId);
            } catch (Exception e) {
                log.error("Failed to expire session for user {}", userId, e);
            }
        }
    }
}
