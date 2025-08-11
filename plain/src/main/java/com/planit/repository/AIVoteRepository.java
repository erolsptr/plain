package com.planit.repository;

import com.planit.model.AIVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AIVoteRepository extends JpaRepository<AIVote, Long> {
    // Belirli bir göreve ait AI oyunu bulmak için bir metod.
    Optional<AIVote> findByTaskId(Long taskId);
}