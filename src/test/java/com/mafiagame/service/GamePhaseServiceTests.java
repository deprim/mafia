package com.mafiagame.service;

import com.mafiagame.model.Game;
import com.mafiagame.model.GamePhase;
import com.mafiagame.repository.GamePhaseRepository;
import com.mafiagame.utils.enums.GamePhaseStatus;
import com.mafiagame.utils.enums.PhaseType;
import com.mafiagame.websocket.GameBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("GamePhaseService")
@ExtendWith(MockitoExtension.class)
class GamePhaseServiceTests {

    @Mock private GamePhaseRepository phases;
    @Mock private GameBroadcaster broadcaster;

    @InjectMocks
    private GamePhaseService service;

    private UUID gameId;
    private Game game;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        game = new Game(1, "test");
        game.setId(gameId);
    }

    @Test
    @DisplayName("should save new opened phase with given game, type and round")
    void shouldSaveNewPhaseOnEnterPhase() {
        service.enterPhase(game, PhaseType.NIGHT, 1);

        ArgumentCaptor<GamePhase> captor = ArgumentCaptor.forClass(GamePhase.class);
        verify(phases).save(captor.capture());

        GamePhase saved = captor.getValue();
        assertThat(saved.getGame()).isSameAs(game);
        assertThat(saved.getPhaseType()).isEqualTo(PhaseType.NIGHT);
        assertThat(saved.getRoundNumber()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(GamePhaseStatus.OPENED);
    }

    @Test
    @DisplayName("should notify mafia only about night voting when entering NIGHT")
    void shouldNotifyMafiaWhenEnteringNight() {
        service.enterPhase(game, PhaseType.NIGHT, 2);

        InOrder order = inOrder(phases, broadcaster);
        order.verify(phases).save(any(GamePhase.class));
        order.verify(broadcaster).toGameAndChatAfterCommit(
                gameId,
                "PHASE_CHANGED",
                Map.of("phase", PhaseType.NIGHT, "round", 2),
                "Night of round 2 has begun."
        );
        order.verify(broadcaster).toMafiaAfterCommit(gameId, "START_NIGHT_VOTING", Map.of("round", 2));

        verifyNoMoreInteractions(broadcaster);
    }

    @Test
    @DisplayName("should notify whole game about day voting when entering DAY")
    void shouldNotifyGameWhenEnteringDay() {
        service.enterPhase(game, PhaseType.DAY, 2);

        InOrder order = inOrder(phases, broadcaster);
        order.verify(phases).save(any(GamePhase.class));
        order.verify(broadcaster).toGameAndChatAfterCommit(
                gameId,
                "PHASE_CHANGED",
                Map.of("phase", PhaseType.DAY, "round", 2),
                "Day of round 2 has begun."
        );
        order.verify(broadcaster).toGameAfterCommit(gameId, "START_DAY_VOTING", Map.of("round", 2));

        verifyNoMoreInteractions(broadcaster);
    }

    @Test
    @DisplayName("should return opened phase from repository on findCurrentPhase")
    void shouldReturnOpenedPhaseOnFindCurrentPhase() {
        GamePhase phase = new GamePhase(game, 1, PhaseType.NIGHT);
        when(phases.findByGameIdAndStatus(gameId, GamePhaseStatus.OPENED)).thenReturn(Optional.of(phase));

        Optional<GamePhase> result = service.findCurrentPhase(game);

        assertThat(result).containsSame(phase);
    }

    @Test
    @DisplayName("should return empty optional on findCurrentPhase when no open phase")
    void shouldReturnEmptyOnFindCurrentPhaseWhenNoOpenPhase() {
        when(phases.findByGameIdAndStatus(gameId, GamePhaseStatus.OPENED)).thenReturn(Optional.empty());

        assertThat(service.findCurrentPhase(game)).isEmpty();
    }

    @Test
    @DisplayName("should return opened phase on currentPhase")
    void shouldReturnOpenedPhaseOnCurrentPhase() {
        GamePhase phase = new GamePhase(game, 1, PhaseType.DAY);
        when(phases.findByGameIdAndStatus(gameId, GamePhaseStatus.OPENED)).thenReturn(Optional.of(phase));

        assertThat(service.currentPhase(game)).isSameAs(phase);
    }

    @Test
    @DisplayName("should throw on currentPhase when game has no open phase")
    void shouldThrowOnCurrentPhaseWhenNoOpenPhase() {
        when(phases.findByGameIdAndStatus(gameId, GamePhaseStatus.OPENED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentPhase(game))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Game has no open phase");
    }

    @Test
    @DisplayName("should close phase, set end time and flush on endPhase")
    void shouldClosePhaseAndSetEndTimeOnEndPhase() {
        GamePhase phase = new GamePhase(game, 1, PhaseType.NIGHT);
        phase.setStatus(GamePhaseStatus.OPENED);

        Instant before = Instant.now();
        service.endPhase(phase);
        Instant after = Instant.now();

        assertThat(phase.getStatus()).isEqualTo(GamePhaseStatus.CLOSED);
        assertThat(phase.getEndedAt()).isBetween(before, after);
        verify(phases).saveAndFlush(phase);
    }

    @Test
    @DisplayName("should throw on endPhase when phase is already closed")
    void shouldThrowOnEndPhaseWhenPhaseAlreadyClosed() {
        GamePhase phase = new GamePhase(game, 1, PhaseType.NIGHT);
        phase.setStatus(GamePhaseStatus.CLOSED);

        assertThatThrownBy(() -> service.endPhase(phase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Phase is already closed");

        assertThat(phase.getEndedAt()).isNull();
        verifyNoInteractions(phases);
    }
}