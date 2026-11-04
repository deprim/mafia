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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final GameResultRepository gameResultRepository;

    @Autowired
    public GameService(GameRepository gameRepository, PlayerRepository playerRepository, GameResultRepository gameResultRepository) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.gameResultRepository = gameResultRepository;
    }


    @Transactional
    public GameCreationResult createGame(CreateGameRequest request){
        Integer mafiaCount = request.mafiaCount();
        String joinCode = JoinCodeGenerator.generateJoinCode();
        String rawSessionToken = SessionTokenGenerator.generateToken();

        // TODO Check if join code already exists

        Game game = new Game(mafiaCount, joinCode);
        gameRepository.save(game);

        Player player = new Player(game,
                request.nickname(),
                true,
                SessionTokenGenerator.hashToken(rawSessionToken)); // Store hashed token in DB

        playerRepository.save(player);


        return new GameCreationResult(GameResponse.from(game), rawSessionToken);

    }

    public GameResponse getGame(UUID id){

        Game game = gameRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Game no found")); // TODO create exception if not found and use orElseThrow

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
        game.setMafiaCount(request.mafiaCount());
        return GameResponse.from(game);

    }

    public GameResultResponse getResult(UUID id){

        GameResult gameResult = gameResultRepository.findByGameId(id)
                .orElseThrow(() -> new RuntimeException("Game results not found"));

        return GameResultResponse.from(gameResult);

    }

    @Transactional
    public void deleteGame(UUID id, String rawSessionToken) {
        Game game = requireWaitingGameForHost(id, rawSessionToken);

        List<Player> players = playerRepository.findByGameId(id);
        playerRepository.deleteAll(players);
        gameRepository.delete(game);
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





}
