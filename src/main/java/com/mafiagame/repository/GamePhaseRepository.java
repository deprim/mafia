package com.mafiagame.repository;

import com.mafiagame.model.GamePhase;
import com.mafiagame.utils.enums.GamePhaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GamePhaseRepository extends JpaRepository<GamePhase, UUID> {
    GamePhase findFirstByGameIdOrderByStartedAtDesc(UUID gameId);
    Optional<GamePhase> findByGameIdAndStatus(UUID gameId, GamePhaseStatus status);
}
