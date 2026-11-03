package com.mafiagame.model;

import com.mafiagame.utils.enums.GamePhaseStatus;
import com.mafiagame.utils.enums.PhaseType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_phase")
@NoArgsConstructor
@Getter
@Setter
public class GamePhase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JoinColumn(name = "game_id")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Game game;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "phase_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PhaseType phaseType;

    @Column(name = "status", nullable = false, length = 15)
    @Enumerated(EnumType.STRING)
    private GamePhaseStatus status = GamePhaseStatus.OPENED;

    @Column(name = "started_at", nullable = false)
    @CreationTimestamp
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public GamePhase(Game game, Integer roundNumber, PhaseType phaseType) {
        this.game = game;
        this.roundNumber = roundNumber;
        this.phaseType = phaseType;
    }
}
