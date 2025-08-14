package com.planit.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteData {
    private String voteValue;
    private Long durationMs; // Oy verme süresi
}