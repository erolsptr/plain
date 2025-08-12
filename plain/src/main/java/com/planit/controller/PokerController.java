package com.planit.controller;

import com.planit.model.Message;
import com.planit.model.RoomState;
import com.planit.model.Task;
import com.planit.model.dto.AIVoteRequest;
import com.planit.model.dto.TaskCreationRequest;
import com.planit.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class PokerController {

    private static final Logger logger = LoggerFactory.getLogger(PokerController.class);

    @Autowired
    private RoomService roomService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/api/rooms")
    public ResponseEntity<Map<String, String>> createRoom(Authentication authentication) {
        String ownerEmail = authentication.getName();
        //String ownerEmail = "erol@example.com";
        String newRoomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        roomService.createRoom(newRoomId, ownerEmail);
        return ResponseEntity.ok(Map.of("roomId", newRoomId));
    }

    @PostMapping("/api/internal/ai-vote")
    public ResponseEntity<Void> receiveAIVote(@RequestBody AIVoteRequest voteRequest) {
        logger.info("Received AI vote from Python server: {}", voteRequest.toString());
        
        roomService.recordAIVote(
            voteRequest.getRoomId(), 
            voteRequest.getVoterName(), 
            voteRequest.getVoteValue(), 
            voteRequest.getReasoning()
        );
        
        publishFullRoomState(voteRequest.getRoomId());
        
        logger.info("AI vote and reasoning processed, full room state broadcasted for room: {}", voteRequest.getRoomId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/rooms")
    public ResponseEntity<Set<Map<String, String>>> getUserRooms(Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(roomService.findRoomsByUserEmail(userEmail));
    }

    @DeleteMapping("/api/rooms/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId, Authentication authentication) {
        String userEmail = authentication.getName();
        roomService.deleteRoom(roomId, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/rooms/{roomId}/save-result")
    public ResponseEntity<Void> saveVotingResult(@PathVariable String roomId, Authentication authentication) {
        String userEmail = authentication.getName();
        roomService.saveCurrentVotingResult(roomId, userEmail);
        publishFullRoomState(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/history-updated", "update");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/rooms/{roomId}/tasks")
    public ResponseEntity<List<Map<String, Object>>> getTaskHistory(@PathVariable String roomId, Authentication authentication) {
        String userEmail = authentication.getName();
        List<Map<String, Object>> history = roomService.getTaskHistoryForRoom(roomId, userEmail);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/api/rooms/{roomId}/tasks")
    public ResponseEntity<Task> createTaskInRoom(
            @PathVariable String roomId,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("cardSet") String cardSet,
            Authentication authentication) {
        
        TaskCreationRequest taskRequest = new TaskCreationRequest();
        taskRequest.setTitle(title);
        taskRequest.setDescription(description);
        taskRequest.setCardSet(cardSet);

        String userEmail = authentication.getName();
        Task createdTask = roomService.createTask(roomId, taskRequest, userEmail);
        
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/history-updated", "update");
        
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTask);
    }

    @GetMapping("/api/rooms/{roomId}/pending-tasks")
    public ResponseEntity<List<Task>> getPendingTasks(@PathVariable String roomId, Authentication authentication) {
        String userEmail = authentication.getName();
        List<Task> pendingTasks = roomService.getPendingTasksForRoom(roomId, userEmail);
        return ResponseEntity.ok(pendingTasks);
    }

    // --- WebSocket Mesaj Eşlemeleri ---
    @MessageMapping("/room/{roomId}/register")
    public void register(@DestinationVariable String roomId, @Payload Message joinMessage, SimpMessageHeaderAccessor headerAccessor) {
        String username = joinMessage.getSender();
        roomService.addUserToRoom(roomId, username);
        headerAccessor.getSessionAttributes().put("username", username);
        // YENİ SATIR: Hangi odada olduğunu oturuma kaydet
        headerAccessor.getSessionAttributes().put("roomId", roomId);
        publishFullRoomState(roomId);
    }

    @MessageMapping("/room/{roomId}/set-task")
    public void setTask(@DestinationVariable String roomId, @Payload Task task, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        String requesterName = (String) headerAccessor.getSessionAttributes().get("username");
        String ownerName = roomService.getRoomOwner(roomId);
        if (ownerName == null || !ownerName.equals(requesterName)) {
            return;
        }

        String requesterEmail = principal.getName();
        
        roomService.setActiveTask(roomId, task, requesterEmail);
    }

    @MessageMapping("/room/{roomId}/vote")
    public void vote(@DestinationVariable String roomId, @Payload Message voteMessage) {
        String username = voteMessage.getSender();
        String voteValue = voteMessage.getContent();
        roomService.recordVote(roomId, username, voteValue);
        publishFullRoomState(roomId);
    }

    @MessageMapping("/room/{roomId}/reveal")
    public void revealVotes(@DestinationVariable String roomId, @Payload Message revealMessage) {
        String requester = revealMessage.getSender();
        String owner = roomService.getRoomOwner(roomId);
        if (owner == null || !owner.equals(requester)) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/reveal", Map.of("reveal", true));
    }
    
    @MessageMapping("/room/{roomId}/new-round")
    public void newRound(@DestinationVariable String roomId, @Payload Message newRoundMessage) {
        String requester = newRoundMessage.getSender();
        String owner = roomService.getRoomOwner(roomId);
        if (owner == null || !owner.equals(requester)) {
            return;
        }
        roomService.startNewRound(roomId);
        publishFullRoomState(roomId);
    }

    @MessageMapping("/room/{roomId}/kick")
    public void kickUser(@DestinationVariable String roomId, @Payload Message kickMessage) {
        String requesterName = kickMessage.getSender();
        String userToKick = kickMessage.getContent();

        String ownerName = roomService.getRoomOwner(roomId);
        if (ownerName == null || !ownerName.equals(requesterName)) {
            logger.warn("Yetkisiz kullanıcı atma denemesi. Odaya Sahibi: {}, İstek Yapan: {}", ownerName, requesterName);
            return;
        }

        logger.info("Oda sahibi '{}', '{}' kullanıcısını {} odasından atıyor.", requesterName, userToKick, roomId);
        roomService.kickUserFromRoom(roomId, userToKick);
        
        publishFullRoomState(roomId);
    }

    public void publishFullRoomState(String roomId) {
    RoomState currentRoomState = new RoomState();
    currentRoomState.setOwner(roomService.getRoomOwner(roomId));
    currentRoomState.setParticipants(roomService.getParticipantsWithAvatars(roomId));
    // YENİ SATIR: Aktif katılımcı listesini ekle
    currentRoomState.setActiveParticipants(roomService.getActiveParticipants(roomId));
    currentRoomState.setActiveTask(roomService.getActiveTask(roomId));
    currentRoomState.setVotes(roomService.getVotes(roomId) != null ? roomService.getVotes(roomId) : Collections.emptyMap());
    currentRoomState.setAreVotesRevealed(false);
    currentRoomState.setAiReasoning(roomService.getAIReasoning(roomId));
    
    messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", currentRoomState);
}
}