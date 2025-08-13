package com.planit.model;

import lombok.Data;
import java.util.Map;
import java.util.Set;

@Data
public class RoomState {

    private String ownerEmail;
    private Map<String, Map<String, String>> participants;    private Task activeTask;
    private Map<String, VoteData> votes;
    private boolean areVotesRevealed;
    private String aiReasoning;
    private Set<String> activeParticipants; 
    private Long votingStartTime; 

}