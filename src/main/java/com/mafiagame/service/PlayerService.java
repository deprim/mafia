package com.mafiagame.service;

import com.mafiagame.model.Elimination;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.Player;
import com.mafiagame.repository.EliminationRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.PlayerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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
     * Marks the player with the specified identifier as dead and saves the updated player.
     *
     * @param playerId the unique identifier of the player to kill
     * @return the nickname of the killed player
     */
    public String kill(UUID playerId, GamePhase current) {
        Elimination elimination = new Elimination();
        elimination.setId(playerId);
        elimination.setEliminatedAt(Instant.now());
        elimination.setPhase(current);
        eliminations.save(elimination);

        Player p = players.findById(playerId).orElseThrow();
        p.setStatus(PlayerStatus.DEAD);
        players.save(p);
        return p.getNickname();
    }


    /**
     * Randomly assigns roles to the provided players and persists the updated players.
     * The player list is shuffled before assignment. Approximately one third of the
     * players are assigned the mafia role, with at least one mafia assigned when the
     * list contains players; all remaining players are assigned the civilian role.
     *
     * @param gameId - id of the game where players should be assigned roles
     */
    public void assignRoles(UUID gameId) {
        List<Player> list = players.findByGameId(gameId);
        Collections.shuffle(list);
        int mafiaCount = Math.max(1, list.size() / 3);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRole(i < mafiaCount ? PlayerRole.MAFIA : PlayerRole.CIVILIAN);
        }
        players.saveAll(list);
    }

    public boolean isHost(UUID playerId, UUID gameId) {
        Player requester = players.findById(playerId).orElseThrow();

        return requester.isHost() && requester.getGame().getId().equals(gameId);
    }

    public boolean isEnoughPlayers(UUID gameId) {
        List<Player> list = players.findByGameId(gameId);
        return  list.size() < MIN_PLAYERS;
    }
}
