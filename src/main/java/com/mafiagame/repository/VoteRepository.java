package com.mafiagame.repository;

import com.mafiagame.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {
    List<Vote> findByPhaseId(UUID phaseId);
    Optional<Vote> findByPhaseIdAndTargetId(UUID phaseId, UUID targetId);
    int countByPhaseId(UUID phaseId);
    Optional<Vote> findByPhaseIdAndVoterId(UUID phaseId, UUID voterId);
}
