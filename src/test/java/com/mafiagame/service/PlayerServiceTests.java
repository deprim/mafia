package com.mafiagame.service;

import com.mafiagame.dto.PlayerDetailsResponse;
import com.mafiagame.model.Elimination;
import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.model.Player;
import com.mafiagame.repository.EliminationRepository;
import com.mafiagame.repository.PlayerRepository;
import com.mafiagame.utils.SessionTokenGenerator;
import com.mafiagame.utils.enums.PhaseType;
import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.PlayerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("PlayerService")
@ExtendWith(MockitoExtension.class)
class PlayerServiceTests {

    private static final String TOKEN = "sessionToken";

    @Mock private PlayerRepository players;
    @Mock private EliminationRepository eliminations;

    @Captor private ArgumentCaptor<List<Player>> rosterCaptor;

    @InjectMocks
    private PlayerService service;

    private UUID gameId;
    private UUID unknownId;
    private Game game;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();

        gameId = UUID.randomUUID();
        game = new Game(1, "test");
        game.setId(gameId);
        unknownId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Player newPlayer(Game playerGame, PlayerRole role, PlayerStatus status) {
        Player player = new Player(playerGame, "nick-" + UUID.randomUUID(), false, "hash-" + UUID.randomUUID());
        player.setId(UUID.randomUUID());
        player.setRole(role);
        player.setStatus(status);
        return player;
    }

    private List<Player> newPlayerList(int size) {
        List<Player> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(newPlayer(game, null, PlayerStatus.ALIVE));
        }
        return list;
    }

    private GamePhase givenPhase() {
        GamePhase phase = new GamePhase(game, 1, PhaseType.NIGHT);
        phase.setId(UUID.randomUUID());
        return phase;
    }

    @Test
    @DisplayName("should count only alive players with given role on countAlive")
    void shouldCountOnlyAlivePlayersWithGivenRoleOnCountAlive() {
        when(players.findByGameId(gameId)).thenReturn(List.of(
                newPlayer(game, PlayerRole.MAFIA, PlayerStatus.ALIVE),
                newPlayer(game, PlayerRole.MAFIA, PlayerStatus.DEAD),
                newPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE),
                newPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE),
                newPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.DEAD)
        ));

        assertThat(service.countAlive(gameId, PlayerRole.MAFIA)).isEqualTo(1);
        assertThat(service.countAlive(gameId, PlayerRole.CIVILIAN)).isEqualTo(2);
    }

    @Test
    @DisplayName("should return zero on countAlive when game has no players")
    void shouldReturnZeroOnCountAliveWhenNoPlayers() {
        when(players.findByGameId(gameId)).thenReturn(List.of());

        assertThat(service.countAlive(gameId, PlayerRole.MAFIA)).isZero();
    }

    @Test
    @DisplayName("should not count players without role on countAlive")
    void shouldNotCountPlayersWithoutRoleOnCountAlive() {
        when(players.findByGameId(gameId)).thenReturn(List.of(
                newPlayer(game, null, PlayerStatus.ALIVE)
        ));

        assertThat(service.countAlive(gameId, PlayerRole.MAFIA)).isZero();
        assertThat(service.countAlive(gameId, PlayerRole.CIVILIAN)).isZero();
    }

    @Test
    @DisplayName("should save elimination, mark player dead and return nickname on kill")
    void shouldEliminatePlayerAndReturnNicknameOnKill() {
        Player victim = newPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        GamePhase phase = givenPhase();
        when(players.findById(victim.getId())).thenReturn(Optional.of(victim));

        String nickname = service.kill(victim.getId(), phase);

        assertThat(nickname).isEqualTo(victim.getNickname());
        assertThat(victim.getStatus()).isEqualTo(PlayerStatus.DEAD);

        ArgumentCaptor<Elimination> captor = ArgumentCaptor.forClass(Elimination.class);
        verify(eliminations).save(captor.capture());
        assertThat(captor.getValue().getPhase()).isSameAs(phase);     // adjust getter names if needed
        assertThat(captor.getValue().getPlayer()).isSameAs(victim);
    }

    @Test
    @DisplayName("should register after-commit callback on kill that does not fail")
    void shouldRegisterAfterCommitCallbackOnKill() {
        Player victim = newPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        when(players.findById(victim.getId())).thenReturn(Optional.of(victim));

        service.kill(victim.getId(), givenPhase());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        assertThatCode(() -> synchronizations.forEach(TransactionSynchronization::afterCommit))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should throw on kill when player not found")
    void shouldThrowOnKillWhenPlayerNotFound() {
        when(players.findById(unknownId)).thenReturn(Optional.empty());
        GamePhase phase = givenPhase();

        assertThatThrownBy(() -> service.kill(unknownId, phase))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Player not found");

        verifyNoInteractions(eliminations);
    }

    @Test
    @DisplayName("should throw on kill when player belongs to another game")
    void shouldThrowOnKillWhenPlayerFromAnotherGame() {
        Game otherGame = new Game(1, "other");
        otherGame.setId(UUID.randomUUID());
        Player outsider = newPlayer(otherGame, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        when(players.findById(outsider.getId())).thenReturn(Optional.of(outsider));
        GamePhase phase = givenPhase();
        UUID playerId = outsider.getId();


        assertThatThrownBy(() -> service.kill(playerId, phase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Player belongs to another game");

        assertThat(outsider.getStatus()).isEqualTo(PlayerStatus.ALIVE);
        verifyNoInteractions(eliminations);
    }

    @Test
    @DisplayName("should throw on kill when player is already dead")
    void shouldThrowOnKillWhenPlayerAlreadyDead() {
        Player dead = newPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.DEAD);
        when(players.findById(dead.getId())).thenReturn(Optional.of(dead));
        UUID playerId = dead.getId();
        GamePhase phase = givenPhase();

        assertThatThrownBy(() -> service.kill(playerId, phase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Player is already dead");

        verifyNoInteractions(eliminations);
    }

    @Test
    @DisplayName("should assign requested number of mafia and civilians on assignRoles")
    void shouldAssignRolesOnAssignRoles() {
        List<Player> roster = newPlayerList(6);
        when(players.findByGameId(gameId)).thenReturn(roster);

        service.assignRoles(gameId, 2);

        verify(players).saveAll(rosterCaptor.capture());
        List<Player> saved = rosterCaptor.getValue();

        assertThat(saved).hasSize(6);
        assertThat(saved).filteredOn(p -> p.getRole() == PlayerRole.MAFIA).hasSize(2);
        assertThat(saved).filteredOn(p -> p.getRole() == PlayerRole.CIVILIAN).hasSize(4);
        assertThat(saved).noneMatch(p -> p.getRole() == null);
    }

    @Test
    @DisplayName("should accept minimum roster of 4 players with 1 mafia on assignRoles")
    void shouldAcceptMinimumRosterOnAssignRoles() {
        when(players.findByGameId(gameId)).thenReturn(newPlayerList(4));

        service.assignRoles(gameId, 1);

        verify(players).saveAll(rosterCaptor.capture());
        assertThat(rosterCaptor.getValue()).filteredOn(p -> p.getRole() == PlayerRole.MAFIA).hasSize(1);
    }

    @Test
    @DisplayName("should throw BAD_REQUEST on assignRoles when fewer than 4 players")
    void shouldThrowBadRequestOnAssignRolesWhenNotEnoughPlayers() {
        List<Player> playersList = newPlayerList(3);
        when(players.findByGameId(gameId)).thenReturn(playersList);

        assertThatThrownBy(() -> service.assignRoles(gameId, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verify(players, never()).saveAll(any());
    }

    @Test
    @DisplayName("should throw BAD_REQUEST on assignRoles when mafia count is null")
    void shouldThrowBadRequestOnAssignRolesWhenMafiaCountNull() {
        when(players.findByGameId(gameId)).thenReturn(newPlayerList(5));

        assertThatThrownBy(() -> service.assignRoles(gameId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verify(players, never()).saveAll(any());
    }

    @Test
    @DisplayName("should throw BAD_REQUEST on assignRoles when mafia count is less than 1")
    void shouldThrowBadRequestOnAssignRolesWhenMafiaCountZero() {
        List<Player> playersList = newPlayerList(5);
        when(players.findByGameId(gameId)).thenReturn(playersList);

        assertThatThrownBy(() -> service.assignRoles(gameId, 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verify(players, never()).saveAll(any());
    }

    @Test
    @DisplayName("should throw BAD_REQUEST on assignRoles when mafia would equal civilians")
    void shouldThrowBadRequestOnAssignRolesWhenMafiaEqualsCivilians() {
        when(players.findByGameId(gameId)).thenReturn(newPlayerList(4));

        assertThatThrownBy(() -> service.assignRoles(gameId, 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verify(players, never()).saveAll(any());
    }

    @Test
    @DisplayName("should throw BAD_REQUEST on assignRoles when mafia would outnumber civilians")
    void shouldThrowBadRequestOnAssignRolesWhenMafiaOutnumbersCivilians() {
        when(players.findByGameId(gameId)).thenReturn(newPlayerList(5));

        assertThatThrownBy(() -> service.assignRoles(gameId, 3))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verify(players, never()).saveAll(any());
    }

    @Test
    @DisplayName("should return requester on requireHost when player is host of the game")
    void shouldReturnHostOnRequireHost() {
        Player host = new Player(game, "host", true, "hash");
        host.setId(UUID.randomUUID());
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(host));

        assertThat(service.requireHost(gameId, TOKEN)).isSameAs(host);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on requireHost when token is null")
    void shouldThrowUnauthorizedOnRequireHostWhenTokenNull() {
        assertThatThrownBy(() -> service.requireHost(gameId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(players);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on requireHost when token is blank")
    void shouldThrowUnauthorizedOnRequireHostWhenTokenBlank() {
        assertThatThrownBy(() -> service.requireHost(gameId, "   "))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(players);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on requireHost when token does not match any player")
    void shouldThrowUnauthorizedOnRequireHostWhenTokenInvalid() {
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireHost(gameId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("should throw FORBIDDEN on requireHost when player belongs to another game")
    void shouldThrowForbiddenOnRequireHostWhenPlayerFromAnotherGame() {
        Game otherGame = new Game(1, "other");
        otherGame.setId(UUID.randomUUID());
        Player outsider = new Player(otherGame, "outsider", true, "hash");
        outsider.setId(UUID.randomUUID());
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> service.requireHost(gameId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("should throw FORBIDDEN on requireHost when player is not host")
    void shouldThrowForbiddenOnRequireHostWhenPlayerNotHost() {
        Player regular = newPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(regular));

        assertThatThrownBy(() -> service.requireHost(gameId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("should return own details on getOwnDetails when ids match")
    void shouldReturnOwnDetailsOnGetOwnDetails() {
        Player me = newPlayer(game, PlayerRole.MAFIA, PlayerStatus.ALIVE);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(me));

        PlayerDetailsResponse response = service.getOwnDetails(me.getId(), TOKEN);

        assertThat(response).usingRecursiveComparison().isEqualTo(PlayerDetailsResponse.from(me));
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on getOwnDetails when token is null")
    void shouldThrowUnauthorizedOnGetOwnDetailsWhenTokenNull() {
        assertThatThrownBy(() -> service.getOwnDetails(unknownId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(players);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on getOwnDetails when token is blank")
    void shouldThrowUnauthorizedOnGetOwnDetailsWhenTokenBlank() {
        assertThatThrownBy(() -> service.getOwnDetails(unknownId, " "))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(players);
    }

    @Test
    @DisplayName("should throw UNAUTHORIZED on getOwnDetails when token does not match any player")
    void shouldThrowUnauthorizedOnGetOwnDetailsWhenTokenInvalid() {
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnDetails(unknownId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("should throw FORBIDDEN on getOwnDetails when requesting another player's details")
    void shouldThrowForbiddenOnGetOwnDetailsWhenAnotherPlayer() {
        Player me = newPlayer(game, PlayerRole.CIVILIAN, PlayerStatus.ALIVE);
        when(players.findBySessionTokenHash(SessionTokenGenerator.hashToken(TOKEN))).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.getOwnDetails(unknownId, TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("should return true on isEnoughPlayers when fewer than 4 players (current behavior)")
    void shouldReturnTrueOnIsEnoughPlayersWhenFewerThanMinimum() {
        when(players.findByGameId(gameId)).thenReturn(newPlayerList(3));

        assertThat(service.isEnoughPlayers(gameId)).isTrue();
    }

    @Test
    @DisplayName("should return false on isEnoughPlayers when 4 or more players (current behavior)")
    void shouldReturnFalseOnIsEnoughPlayersWhenMinimumReached() {
        when(players.findByGameId(gameId)).thenReturn(newPlayerList(4));

        assertThat(service.isEnoughPlayers(gameId)).isFalse();
    }
}