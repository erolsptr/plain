package com.planit.repository;

import com.planit.model.Task;
import com.planit.model.User;
import com.planit.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    long countByTaskId(Long taskId);
}