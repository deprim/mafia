package com.mafiagame.repository;

import com.mafiagame.model.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GameResultRepository extends JpaRepository<GameResult, UUID> {
}
