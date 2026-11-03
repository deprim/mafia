package com.mafiagame.repository;

import com.mafiagame.model.Elimination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EliminationRepository extends JpaRepository<Elimination, UUID> {
}
