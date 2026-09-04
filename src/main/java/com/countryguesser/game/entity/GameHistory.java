package com.countryguesser.game.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="games_history")
@Getter
@Setter
@NoArgsConstructor
public class GameHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer streak;

    @Column(name = "ended_reason", nullable = false, length = 20)
    private String endedReason;

    @Column(name = "played_at", nullable = false, updatable = false)
    private LocalDateTime playedAt = LocalDateTime.now();
}
