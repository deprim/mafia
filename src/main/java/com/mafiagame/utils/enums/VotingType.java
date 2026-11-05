package com.mafiagame.utils.enums;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public enum VotingType {
    MAFIA(PhaseType.NIGHT, EnumSet.of(PlayerRole.MAFIA)),
    CIVILIAN(PhaseType.DAY, EnumSet.allOf(PlayerRole.class));

    private final PhaseType phase;
    private final Set<PlayerRole> allowedVoters;

    /**
     * Creates a voting type for the specified game phase and permitted voter roles.
     *
     * @param phase the game phase in which this voting type applies
     * @param allowedVoters the set of player roles allowed to vote in this voting type
     */
    VotingType(PhaseType phase, Set<PlayerRole> allowedVoters) {
        this.phase = phase;
        this.allowedVoters = allowedVoters;
    }

    /**
     * Returns the voting type associated with the specified game phase.
     *
     * @param phaseType the phase type to find a voting type for
     * @return an {@link Optional} containing the matching voting type, or an empty {@link Optional} if no voting type is associated with the phase
     */
    public static Optional<VotingType> forPhase(PhaseType phaseType) {
        return Arrays.stream(values())
                .filter(v -> v.phase == phaseType)
                .findFirst();
    }

    /**
     * Determines whether a player with the specified role is allowed to vote in this voting type.
     *
     * @param role the player role to check for voting eligibility
     * @return true if the specified role is allowed to vote; false otherwise
     */
    public boolean canVote(PlayerRole role) {
        return allowedVoters.contains(role);
    }
}