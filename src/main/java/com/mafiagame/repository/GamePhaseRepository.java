package com.mafiagame.repository;

import com.mafiagame.model.GamePhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GamePhaseRepository extends JpaRepository<GamePhase, UUID> {
}
