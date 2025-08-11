package com.planit.repository;

import com.planit.model.Task;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    // --- BU SATIRI EKLE ---
    List<Task> findByPokerRoomId(String roomId);
}