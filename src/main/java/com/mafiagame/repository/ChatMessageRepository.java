package com.mafiagame.repository;

import com.mafiagame.model.ChatMessageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageRecord, Long> {
    List<ChatMessageRecord> findTop100ByGameIdOrderByIdDesc(UUID gameId);
}
