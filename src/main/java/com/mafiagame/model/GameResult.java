package com.mafiagame.model;

import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_result")
@NoArgsConstructor
@Getter
@Setter
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    @Column(name = "winning_team", length = 20)
    @Enumerated(EnumType.STRING)
    private Team winningTeam;

    @Column(name = "finished_at")
    @CreationTimestamp
    private Instant finishedAt;

    public GameResult(Game game, Team winningTeam) {
        this.game = game;
        this.winningTeam = winningTeam;
    }
}
