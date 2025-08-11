package com.planit.model;

import lombok.Data;
import java.util.Map;
import java.util.Set;

@Data
public class RoomState {

    private String owner;
    private Map<String, String> participants; 
    private Task activeTask;
    private Map<String, String> votes;
    private boolean areVotesRevealed;
    private String aiReasoning;
    
    // YENİ ALAN: Aktif (çevrimiçi) olan katılımcıların isimlerini tutar
    private Set<String> activeParticipants; 
}