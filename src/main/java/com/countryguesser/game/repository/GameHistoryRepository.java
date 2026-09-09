package com.countryguesser.game.repository;

import com.countryguesser.game.entity.GameHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameHistoryRepository extends JpaRepository<GameHistory, Long> {

    List<GameHistory> findByUserIdOrderByPlayedAtDesc(Long userId);
}
