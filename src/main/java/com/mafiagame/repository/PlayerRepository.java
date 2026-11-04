package com.mafiagame.repository;

import com.mafiagame.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {
    List<Player> findByGameId(UUID gameId);
    Optional<Player> findBySessionTokenHash(String sessionTokenHash);
}
