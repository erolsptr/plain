package com.planit.model.dto;

import lombok.Data;

@Data
public class AIVoteRequest {
    private String roomId;
    private String voterName;
    private String voteValue;
    private String reasoning;
}