package com.mafiagame.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "elimination")
@NoArgsConstructor
@Setter
@Getter
public class Elimination {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phase_id")
    private GamePhase phase;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(name = "eliminated_at", updatable = false)
    @CreationTimestamp
    private Instant eliminatedAt;

    public Elimination(GamePhase phase, Player player) {
        this.phase = phase;
        this.player = player;
    }
}
