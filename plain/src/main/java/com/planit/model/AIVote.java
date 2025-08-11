package com.planit.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_votes")
@Data
@NoArgsConstructor
public class AIVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String voteValue;

    // YENİ ALAN: AI'ın gerekçesini saklar
    @Column(columnDefinition = "TEXT", nullable = true)
    private String reasoning;

    @OneToOne
    @JoinColumn(name = "task_id", referencedColumnName = "id", unique = true)
    private Task task;
}