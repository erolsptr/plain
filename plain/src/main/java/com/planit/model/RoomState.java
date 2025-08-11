package com.planit.model;

import lombok.Data;
import java.util.Map;
import java.util.Set;

@Data
public class RoomState {

    private String owner;
    
    // DEĞİŞİKLİK: Katılımcıları artık isim -> avatarId şeklinde bir harita olarak tutacağız.
    private Map<String, String> participants; 

    private Task activeTask;
    private Map<String, String> votes;
    private boolean areVotesRevealed;
    private String aiReasoning;

}