package com.mafiagame.repository;

import com.mafiagame.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {
}
