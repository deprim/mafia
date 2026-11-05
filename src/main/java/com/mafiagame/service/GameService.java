package com.mafiagame.service;

import com.mafiagame.dto.*;
import com.mafiagame.model.Game;
import com.mafiagame.model.GameResult;
import com.mafiagame.model.Player;
import com.mafiagame.repository.GameRepository;
import com.mafiagame.repository.GameResultRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.JoinCodeGenerator;
import com.mafiagame.utils.SessionTokenGenerator;
import com.mafiagame.utils.enums.GameStatus;
import com.mafiagame.websocket.GameBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final GameResultRepository gameResultRepository;
    private final GameBroadcaster broadcaster;

    @Autowired
    public GameService(
            GameRepository gameRepository,
            PlayerRepository playerRepository,
            GameResultRepository gameResultRepository,
            GameBroadcaster broadcaster
    ) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.gameResultRepository = gameResultRepository;
        this.broadcaster = broadcaster;
    }


    @Transactional
    public GameCreationResult createGame(CreateGameRequest request){
        String nickname = requireNickname(request.nickname());
        Integer mafiaCount = request.mafiaCount();
        if (mafiaCount == null || mafiaCount < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mafia count must be at least 1");
        }
        String joinCode = JoinCodeGenerator.generateJoinCode();
        String rawSessionToken = SessionTokenGenerator.generateToken();

        // TODO Check if join code already exists

        Game game = new Game(mafiaCount, joinCode);
        gameRepository.save(game);

        Player player = new Player(game,
                nickname,
                true,
                SessionTokenGenerator.hashToken(rawSessionToken)); // Store hashed token in DB

        playerRepository.save(player);
        log.info("Game created with ID {} and host player with ID {}", game.getId(), player.getId());



        return new GameCreationResult(GameResponse.from(game), rawSessionToken);

    }

    @Transactional
    public PlayerJoinResult joinGame(JoinGameRequest request) {
        String joinCode = request.joinCode() == null
                ? ""
                : request.joinCode().trim().toUpperCase(Locale.ROOT);
        if (joinCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room code is required");
        }

        Game game = gameRepository.findByJoinCode(joinCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        if (game.getStatus() != GameStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is no longer accepting players");
        }

        String nickname = requireNickname(request.nickname());
        boolean nicknameTaken = playerRepository.findByGameId(game.getId()).stream()
                .anyMatch(player -> player.getNickname().equalsIgnoreCase(nickname));
        if (nicknameTaken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That nickname is already used in this room");
        }

        String rawSessionToken = SessionTokenGenerator.generateToken();
        Player player = new Player(
                game,
                nickname,
                false,
                SessionTokenGenerator.hashToken(rawSessionToken)
        );


        playerRepository.save(player);
        log.info("Player with ID {} joined game {}:", player.getId(), game.getId());

        return new PlayerJoinResult(
                game.getId(),
                new PlayerResponse(player.getId(), player.getNickname(), player.isHost(), player.getStatus()),
                rawSessionToken
        );
    }

    public GameResponse getGame(UUID id){

        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        return GameResponse.from(game);

    }

    public GameResponse getGameByJoinCode(String joinCode){
        Game game = gameRepository.findByJoinCode(joinCode)
                .orElseThrow(() -> new RuntimeException("Game with join code " + joinCode + " not found")); // TODO create exception if not found and use orElseThrow

        return GameResponse.from(game);
    }


    @Transactional
    public GameResponse updateGameSettings(UUID id, UpdateGameSettingsRequest request, String rawSessionToken){

        Game game = requireWaitingGameForHost(id, rawSessionToken);
        log.info("Updating game settings for game {}, old mafiaCount = {}, new mafiaCount = {}", id, game.getMafiaCount(), request.mafiaCount());
        game.setMafiaCount(request.mafiaCount());
        return GameResponse.from(game);

    }

    public GameResultResponse getResult(UUID id){

        GameResult gameResult = gameResultRepository.findByGameId(id)
                .orElseThrow(() -> new RuntimeException("Game results not found"));

        return GameResultResponse.from(gameResult);

    }

    /**
     * Deletes a waiting game owned by the host identified by the provided session token.
     *
     * Removes all players associated with the game, deletes the game record, and schedules
     * a room-closed notification to be broadcast after the transaction commits successfully.
     *
     * @param id the unique identifier of the game to delete
     * @param rawSessionToken the raw session token used to verify that the requester is the game host
     */
    @Transactional
    public void deleteGame(UUID id, String rawSessionToken) {
        Game game = requireWaitingGameForHost(id, rawSessionToken);

        List<Player> players = playerRepository.findByGameId(id);
        playerRepository.deleteAll(players);
        gameRepository.delete(game);
        log.info("Deleting game {} with {} players", id, players.size());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broadcaster.toGame(id, "ROOM_CLOSED", Map.of("gameId", id));
            }
        });
    }


    /**
     * Ensures that a game in a "waiting" state exists and the specified player is its host.
     * Validates the session token and retrieves the associated game if the conditions are met.
     *
     * @param gameId    the unique identifier of the game to be validated
     * @param rawToken  the raw session token of the player to authenticate
     * @return the game instance that meets the specified conditions
     * @throws RuntimeException if the session token is null or blank
     * @throws RuntimeException if the player associated with the token is not found
     * @throws RuntimeException if the player is not the host of the specified game
     * @throws RuntimeException if the game is not in the "waiting" state
     */
    private Game requireWaitingGameForHost(UUID gameId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RuntimeException("Token is required for this action"); // TODO create custom exception
        }

        String tokenHash = SessionTokenGenerator.hashToken(rawToken);

        Player player = playerRepository.findBySessionTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Player not found")); // TODO create custom exception

        Game game = player.getGame();

        if (!game.getId().equals(gameId) || !player.isHost()) {
            throw new RuntimeException("Player is not host of this game"); // TODO create custom exception
        }

        if (game.getStatus() != GameStatus.WAITING) {
            throw new RuntimeException("Game is not in waiting state"); // TODO create custom exception
        }

        return game;
    }

    private String requireNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname is required");
        }
        String normalized = nickname.trim();
        if (normalized.length() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname must be 40 characters or fewer");
        }
        return normalized;
    }
}
