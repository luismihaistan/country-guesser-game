package com.countryguesser.game.repository;

import com.countryguesser.game.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import org.springframework.data.repository.query.Param;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.maxStreak = GREATEST(u.maxStreak, :streak), " +
            "u.totalGames = u.totalGames + 1 WHERE u.id = :userId")
    void updateStatsAfterGame(@Param("userId") Long userId, @Param("streak") Integer streak);
}
