package com.mafiagame.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vote")
@NoArgsConstructor
@Getter
@Setter
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phase_id")
    private GamePhase phase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_id")
    private Player voter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id")
    private Player target;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant votedAt;

    public Vote(GamePhase phase, Player voter, Player target) {
        this.phase = phase;
        this.voter = voter;
        this.target = target;
    }

}
