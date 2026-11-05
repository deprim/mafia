package com.mafiagame.repository;

import com.mafiagame.model.Game;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findByJoinCode(String joinCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select game from Game game where game.id = :id")
    Optional<Game> findByIdForUpdate(@Param("id") UUID id);

}
