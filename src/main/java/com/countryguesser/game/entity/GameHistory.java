package com.countryguesser.game.entity;

import com.countryguesser.game.entity.enums.EndedReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "ended_reason", nullable = false, length = 20)
    private EndedReason endedReason;

    @CreationTimestamp
    @Column(name = "played_at", nullable = false, updatable = false)
    private LocalDateTime playedAt;
}
