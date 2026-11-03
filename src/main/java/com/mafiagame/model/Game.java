package com.mafiagame.model;

import com.mafiagame.utils.JoinCodeGenerator;
import com.mafiagame.utils.enums.GameStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "game")
@NoArgsConstructor
@Getter
@Setter
public class Game {

    @GeneratedValue(strategy = GenerationType.UUID)
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "join_code", length = 8, unique = true)
    private String joinCode;

    @Column(name = "mafia_count", nullable = false)
    private Integer mafiaCount;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private GameStatus status = GameStatus.WAITING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    public Game(Integer mafiaCount, String joinCode) {
        this.mafiaCount = mafiaCount;
        this.joinCode = joinCode;
    }

}
