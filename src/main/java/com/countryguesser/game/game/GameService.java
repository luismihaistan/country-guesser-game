package com.countryguesser.game.game;

import com.countryguesser.game.entity.GameHistory;
import com.countryguesser.game.entity.User;
import com.countryguesser.game.entity.enums.EndedReason;
import com.countryguesser.game.redis.GameSession;
import com.countryguesser.game.redis.LocationEntry;
import com.countryguesser.game.redis.LocationPoolService;
import com.countryguesser.game.redis.SessionService;
import com.countryguesser.game.repository.GameHistoryRepository;
import com.countryguesser.game.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameService {

    private final LocationPoolService locationPoolService;
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final GameHistoryRepository gameHistoryRepository;

    @Transactional
    public StartRoundResult startRound(Long userId) {
        expireSessionIfNeeded(userId);

        if (sessionService.hasActiveRound(userId)) {
            return new StartRoundResult(StartRoundResult.Status.ALREADY_ACTIVE, null);
        }

        Optional<LocationEntry> locationOpt = locationPoolService.pop();
        if (locationOpt.isEmpty()) {
            return new StartRoundResult(StartRoundResult.Status.POOL_EMPTY, null);
        }

        LocationEntry location = locationOpt.get();
        int pendingStreak = sessionService.getPendingStreak(userId);

        sessionService.startRound(userId, location.panoId(), location.countryCode(), pendingStreak);

        RoundResponse round = new RoundResponse(
                location.panoId(), pendingStreak, sessionService.getRemainingSeconds(userId));
        return new StartRoundResult(StartRoundResult.Status.STARTED, round);
    }

    @Transactional
    public GuessResult submitGuess(Long userId, String guessedCountryCode) {
        Optional<GameSession> expiredOpt = expireSessionIfNeeded(userId);
        if (expiredOpt.isPresent()) {
            GameSession expired = expiredOpt.get();
            return new GuessResult(GuessResult.Status.NO_ACTIVE_ROUND, false, expired.streak(), expired.countryCode());
        }

        Optional<GameSession> sessionOpt = sessionService.getActiveRound(userId);
        if (sessionOpt.isEmpty()) {
            return new GuessResult(GuessResult.Status.NO_ACTIVE_ROUND, false, 0, null);
        }

        GameSession session = sessionOpt.get();
        boolean correct = session.countryCode().equalsIgnoreCase(guessedCountryCode);

        if (correct) {
            int newStreak = session.streak() + 1;
            sessionService.markCorrectGuess(userId, newStreak);
            return new GuessResult(GuessResult.Status.CORRECT, true, newStreak, null);
        }

        int finalStreak = session.streak();
        persistGameEnd(userId, finalStreak, EndedReason.WRONG_GUESS);
        sessionService.clearSession(userId);

        return new GuessResult(GuessResult.Status.INCORRECT, false, finalStreak, session.countryCode());
    }

    @Transactional
    public Optional<RoundResponse> getCurrentRound(Long userId) {
        expireSessionIfNeeded(userId);
        return sessionService.getActiveRound(userId)
                .map(s -> new RoundResponse(
                        s.panoId(), s.streak(), sessionService.getRemainingSeconds(userId)));
    }

    @Transactional
    public TimeoutResult checkTimeout(Long userId) {
        Optional<GameSession> expiredOpt = expireSessionIfNeeded(userId);
        if (expiredOpt.isPresent()) {
            GameSession expired = expiredOpt.get();
            return new TimeoutResult(true, 0, expired.streak(), expired.countryCode());
        }

        if (sessionService.hasActiveRound(userId)) {
            return new TimeoutResult(false, sessionService.getRemainingSeconds(userId), null, null);
        }

        return new TimeoutResult(true, 0, null, null);
    }

    @Transactional
    public Optional<GameSession> expireSessionIfNeeded(Long userId) {
        Optional<GameSession> expiredOpt = sessionService.claimExpiredSession(userId);
        if (expiredOpt.isEmpty()) {
            return Optional.empty();
        }

        GameSession expired = expiredOpt.get();
        if (expired.panoId() != null || expired.streak() > 0) {
            persistGameEnd(userId, expired.streak(), EndedReason.ABANDONED);
        }
        return Optional.of(expired);
    }

    private void persistGameEnd(Long userId, int streak, EndedReason reason) {
        GameHistory history = new GameHistory();
        history.setUser(userRepository.getReferenceById(userId));
        history.setStreak(streak);
        history.setEndedReason(reason);
        gameHistoryRepository.save(history);

        userRepository.updateStatsAfterGame(userId, streak);
    }
}