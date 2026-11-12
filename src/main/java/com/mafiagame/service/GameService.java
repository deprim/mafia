package com.mafiagame.service;

import com.mafiagame.exception.ApiException;
import com.mafiagame.exception.GameAccessDeniedException;
import com.mafiagame.exception.GameConflictException;
import com.mafiagame.exception.InvalidSessionException;
import com.mafiagame.exception.ResourceNotFoundException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final GameResultRepository gameResultRepository;
    private final GamePhaseService gamePhaseService;
    private final GameBroadcaster broadcaster;
    private final ChatHistoryService chatHistory;

    @Autowired
    public GameService(
            GameRepository gameRepository,
            PlayerRepository playerRepository,
            GameResultRepository gameResultRepository,
            GamePhaseService gamePhaseService,
            GameBroadcaster broadcaster,
            ChatHistoryService chatHistory
    ) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.gameResultRepository = gameResultRepository;
        this.gamePhaseService = gamePhaseService;
        this.broadcaster = broadcaster;
        this.chatHistory = chatHistory;
    }


    @Transactional
    public GameCreationResult createGame(CreateGameRequest request) {
        String nickname = request.nickname();
        String joinCode = JoinCodeGenerator.generateJoinCode();
        String rawSessionToken = SessionTokenGenerator.generateToken();


        Game game = new Game(1, joinCode);
        gameRepository.save(game);

        Player player = new Player(game,
                nickname,
                true,
                SessionTokenGenerator.hashToken(rawSessionToken)); // Store hashed token in DB

        playerRepository.save(player);
        chatHistory.saveSystemMessage(game.getId(), nickname + " created the room.");
        log.info("Game created with ID {} and host player with ID {}", game.getId(), player.getId());


        return new GameCreationResult(GameResponse.from(game), rawSessionToken);

    }

    @Transactional
    public PlayerJoinResult joinGame(JoinGameRequest request) {
        String joinCode = request.joinCode();

        Game game = gameRepository.findByJoinCode(joinCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        if (game.getStatus() != GameStatus.WAITING) {
            throw new GameConflictException("Room is no longer accepting players");
        }

        String nickname = request.nickname();
        boolean nicknameTaken = playerRepository.findByGameId(game.getId()).stream()
                .anyMatch(player -> player.getNickname().equalsIgnoreCase(nickname));
        if (nicknameTaken) {
            throw new GameConflictException("That nickname is already used in this room");
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

        PlayerResponse playerResponse = new PlayerResponse(
                player.getId(),
                player.getNickname(),
                player.isHost(),
                player.getStatus()
        );
        broadcaster.toGameAndChatAfterCommit(
                game.getId(),
                "PLAYER_JOINED",
                playerResponse,
                playerResponse.nickname() + " joined the room."
        );

        return new PlayerJoinResult(
                game.getId(),
                playerResponse,
                rawSessionToken
        );
    }


    /**
     * Retrieves the current state of the game room with the specified identifier.
     *
     * @param id the unique identifier of the game room to retrieve
     * @return the current game state, including room details, players, current phase, and result if available
     * @throws ApiException if no game room exists with the specified identifier
     */
    public GameStateResponse getRoomState(UUID id) {
        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        List<PlayerResponse> players = playerRepository.findByGameId(id).stream()
                .map(player -> new PlayerResponse(
                        player.getId(),
                        player.getNickname(),
                        player.isHost(),
                        player.getStatus()
                ))
                .toList();

        CurrentPhaseResponse currentPhase = gamePhaseService.findCurrentPhase(game)
                .map(CurrentPhaseResponse::from)
                .orElse(null);
        GameResultResponse result = gameResultRepository.findByGameId(id)
                .map(GameResultResponse::from)
                .orElse(null);

        return new GameStateResponse(
                game.getId(),
                game.getJoinCode(),
                game.getMafiaCount(),
                game.getStatus(),
                players,
                currentPhase,
                result
        );
    }


    /**
     * Retrieves the result for the game with the specified identifier.
     *
     * @param id the unique identifier of the game whose result is to be retrieved
     * @return the game result response for the specified game
     * @throws ApiException if no game result is found for the specified identifier
     */
    public GameResultResponse getResult(UUID id) {

        GameResult gameResult = gameResultRepository.findByGameId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Game result not found"));

        return GameResultResponse.from(gameResult);

    }

    /**
     * Deletes a waiting game owned by the host identified by the provided session token.
     * <p>
     * Removes all players associated with the game, deletes the game record, and schedules
     * a room-closed notification to be broadcast after the transaction commits successfully.
     *
     * @param id              the unique identifier of the game to delete
     * @param rawSessionToken the raw session token used to verify that the requester is the game host
     */
    @Transactional
    public void deleteGame(UUID id, String rawSessionToken) {
        Game game = requireWaitingGameForHost(id, rawSessionToken);

        List<Player> players = playerRepository.findByGameId(id);
        playerRepository.deleteAll(players);
        gameRepository.delete(game);
        log.info("Deleting game {} with {} players", id, players.size());
        broadcaster.toGameAfterCommit(id, "ROOM_CLOSED", Map.of("gameId", id));
    }


    /**
     * Ensures that a game in a "waiting" state exists and the specified player is its host.
     * Validates the session token and retrieves the associated game if the conditions are met.
     *
     * @param gameId   the unique identifier of the game to be validated
     * @param rawToken the raw session token of the player to authenticate
     * @return the game instance that meets the specified conditions
     * @throws InvalidSessionException if the session token is missing or invalid
     * @throws GameAccessDeniedException if the player is not the host of this game
     * @throws GameConflictException if the game is not waiting
     */
    private Game requireWaitingGameForHost(UUID gameId, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidSessionException("Token is required for this action");
        }

        String tokenHash = SessionTokenGenerator.hashToken(rawToken);

        Player player = playerRepository.findBySessionTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidSessionException("Invalid session token"));

        Game game = player.getGame();

        if (!game.getId().equals(gameId) || !player.isHost()) {
            throw new GameAccessDeniedException("Player is not host of this game");
        }

        if (game.getStatus() != GameStatus.WAITING) {
            throw new GameConflictException("Game is not in waiting state");
        }

        return game;
    }

}
