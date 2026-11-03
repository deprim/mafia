package com.mafiagame.model;

import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.PlayerStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player")
@NoArgsConstructor
@Getter
@Setter
public class Player {

    @GeneratedValue(strategy = GenerationType.UUID)
    @Id
    @Column(name = "id")
    private UUID id;

    @JoinColumn(name = "game_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Game game;

    @Column(name = "nickname", length = 40, nullable = false)
    private String nickname;

    @Column(name = "is_host", nullable = false)
    private boolean isHost;

    @Column(name = "role", length = 10)
    @Enumerated(EnumType.STRING)
    private PlayerRole role; // Role is set after game starts, no need in constructor

    @Column(name = "status", nullable = false, length = 15)
    @Enumerated(EnumType.STRING)
    private PlayerStatus status = PlayerStatus.ALIVE; // Default status is alive

    @Column(name = "session_token_hash", length = 64, nullable = false, unique = true)
    private String sessionTokenHash;

    @Column(name = "joined_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant joinedAt;

    public Player(Game game, String nickname, boolean isHost, String sessionTokenHash) {
        this.game = game;
        this.nickname = nickname;
        this.isHost = isHost;
        this.sessionTokenHash = sessionTokenHash;
    }
}
