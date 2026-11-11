package com.mafiagame.service;

import com.mafiagame.dto.PlayerDetailsResponse;
import com.mafiagame.model.Elimination;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.Player;
import com.mafiagame.repository.EliminationRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.SessionTokenGenerator;
import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.PlayerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PlayerService {

    private static final int MIN_PLAYERS = 4;

    private final PlayerRepository players;
    private final EliminationRepository eliminations;

    /**
     * Counts the players in the given list who have the specified role and are alive.
     *
     * @param role the role to count among alive players
     * @return the number of alive players with the specified role
     */
    public long countAlive(UUID gameId, PlayerRole role) {
        List<Player> all = players.findByGameId(gameId);
        return all.stream()
                .filter(p -> p.getRole() == role && p.getStatus() == PlayerStatus.ALIVE)
                .count();
    }


    /**
     * Eliminates the specified player during the given game phase.
     * Records the elimination, marks the player as dead, and returns the eliminated
     * player's nickname.
     *
     * @param playerId the unique identifier of the player to eliminate
     * @param current the current game phase in which the elimination occurs
     * @return the nickname of the eliminated player
     * @throws RuntimeException if the player is not found, belongs to another game, or is already dead
     */
    @Transactional
    public String kill(UUID playerId, GamePhase current) {
        Player player = players.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found"));

        UUID gameId = current.getGame().getId();
        UUID phaseId = current.getId();

        if (!player.getGame().getId().equals(gameId)) throw new IllegalStateException("Player belongs to another game");

        if (player.getStatus() != PlayerStatus.ALIVE) throw new IllegalStateException("Player is already dead");

        eliminations.save(new Elimination(current, player));
        player.setStatus(PlayerStatus.DEAD);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.info(
                                "Player eliminated: gameId= {}, phaseId= {}, playerId= {}",
                                gameId, phaseId, playerId
                        );
                    }
                }
        );

        return player.getNickname();
    }


    /**
     * Randomly assigns the configured Mafia count after validating the roster.
     *
     * @param gameId the game whose players receive roles
     * @param configuredMafiaCount the Mafia count configured for the game
     */
    @Transactional
    public void assignRoles(UUID gameId, Integer configuredMafiaCount) {
        List<Player> roster = players.findByGameId(gameId);
        if (roster.size() < MIN_PLAYERS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Need at least " + MIN_PLAYERS + " players");
        if (configuredMafiaCount == null || configuredMafiaCount < 1 || 2L * configuredMafiaCount >= roster.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mafia count must be at least 1 and fewer than the number of Civilians");

        Collections.shuffle(roster);
        for (int i = 0; i < roster.size(); i++)
            roster.get(i).setRole(i < configuredMafiaCount ? PlayerRole.MAFIA : PlayerRole.CIVILIAN);
        players.saveAll(roster);
        log.info("Roles assigned to players in game {}, playersCount = {}, mafiaCount = {}", gameId, roster.size(), configuredMafiaCount);
    }

    public Player requireHost(UUID gameId, String rawSessionToken) {
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A session token is required");
        }

        Player requester = players.findBySessionTokenHash(SessionTokenGenerator.hashToken(rawSessionToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token"));

        if (!requester.getGame().getId().equals(gameId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player is not part of this room");
        if (!requester.isHost())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can manage this game");

        return requester;
    }

    public PlayerDetailsResponse getOwnDetails(UUID playerId, String rawSessionToken) {
        if (rawSessionToken == null || rawSessionToken.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A session token is required");

        Player requester = players.findBySessionTokenHash(SessionTokenGenerator.hashToken(rawSessionToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token"));

        if (!requester.getId().equals(playerId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Players can only access their own details");

        return PlayerDetailsResponse.from(requester);
    }

}
