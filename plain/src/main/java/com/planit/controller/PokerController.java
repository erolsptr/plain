package com.planit.controller;

import com.planit.model.Message;
import com.planit.model.RoomState;
import com.planit.model.Task;
import com.planit.model.User;
import com.planit.model.dto.AIVoteRequest;
import com.planit.model.dto.TaskCreationRequest;
import com.planit.repository.UserRepository;
import com.planit.service.RoomService;
import com.planit.service.JiraService; // YENİ: JiraService'i import et
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
import org.springframework.security.access.AccessDeniedException;

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
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private JiraService jiraService;

    @PostMapping("/api/rooms")
    public ResponseEntity<Map<String, String>> createRoom(Authentication authentication) {
        String ownerEmail = authentication.getName();
        String newRoomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        roomService.createRoom(newRoomId, ownerEmail);
        return ResponseEntity.ok(Map.of("roomId", newRoomId));
    }

    @PostMapping("/api/rooms/{roomId}/tasks/{taskId}/send-to-jira")
    public ResponseEntity<Map<String, String>> sendTaskToJira(
            @PathVariable String roomId,
            @PathVariable Long taskId,
            @RequestBody Map<String, String> payload,
            Authentication authentication) {

        String userEmail = authentication.getName();
        String consensusScore = payload.get("consensusScore");

        try {
            String issueKey = jiraService.createJiraIssue(taskId, consensusScore, userEmail);
            return ResponseEntity.ok(Map.of("message", "Görev başarıyla Jira'ya gönderildi!", "issueKey", issueKey));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Jira'ya gönderme başarısız: " + e.getMessage()));
        }
    }

    @PostMapping("/api/internal/ai-vote")
    public ResponseEntity<Void> receiveAIVote(@RequestBody AIVoteRequest voteRequest) {
        logger.info("Received AI vote from Python server: {}", voteRequest.toString());
        roomService.recordAIVote(
                voteRequest.getRoomId(),
                voteRequest.getVoterName(),
                voteRequest.getVoteValue(),
                voteRequest.getReasoning());
        publishFullRoomState(voteRequest.getRoomId());
        logger.info("AI vote and reasoning processed, full room state broadcasted for room: {}",
                voteRequest.getRoomId());
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

    // --- PokerController.java İÇİNE BU YENİ ENDPOINT'İ EKLE ---

    // --- PokerController.java İÇİNDEKİ BU METODU DEĞİŞTİR ---

    @DeleteMapping("/api/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long taskId, Authentication authentication) {
        String userEmail = authentication.getName();
        try {
            // Önce görevin hangi odaya ait olduğunu bulmamız gerekiyor.
            Task taskToDelete = roomService.findTaskById(taskId); // Bu metodu birazdan ekleyeceğiz
            if (taskToDelete == null) {
                return ResponseEntity.notFound().build();
            }
            String roomId = taskToDelete.getPokerRoom().getId();

            // Silme işlemini yap
            roomService.deleteTask(taskId, userEmail);

            // YENİ: Odaya "geçmiş güncellendi" mesajı yayınla
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/history-updated", "update");

            return ResponseEntity.noContent().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // Oylanmış görev silinemez
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
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
    public ResponseEntity<List<Map<String, Object>>> getTaskHistory(@PathVariable String roomId,
            Authentication authentication) {
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

    @MessageMapping("/room/{roomId}/register")
    public void register(@DestinationVariable String roomId, @Payload Message joinMessage,
            SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userEmail));

        roomService.addUserToRoom(roomId, user.getName());
        headerAccessor.getSessionAttributes().put("username", user.getName());
        headerAccessor.getSessionAttributes().put("roomId", roomId);
        publishFullRoomState(roomId);
    }

    @MessageMapping("/room/{roomId}/set-task")
    public void setTask(@DestinationVariable String roomId, @Payload Task task, Principal principal) {
        String requesterEmail = principal.getName();
        String ownerEmail = roomService.getRoomOwnerEmail(roomId);

        if (ownerEmail == null || !ownerEmail.equals(requesterEmail)) {
            logger.warn("Yetkisiz görev başlatma denemesi. Oda Sahibi E-postası: {}, İstek Yapan: {}", ownerEmail,
                    requesterEmail);
            return;
        }

        roomService.setActiveTask(roomId, task, requesterEmail);
    }

    @MessageMapping("/room/{roomId}/vote")
    public void vote(@DestinationVariable String roomId, @Payload Message voteMessage) {
        String username = voteMessage.getSender();
        String voteValue = voteMessage.getContent();
        roomService.recordVote(roomId, username, voteValue, voteMessage.getDurationMs());
        publishFullRoomState(roomId);
    }

    @MessageMapping("/room/{roomId}/reveal")
    public void revealVotes(@DestinationVariable String roomId, Principal principal) {
        String requesterEmail = principal.getName();
        String ownerEmail = roomService.getRoomOwnerEmail(roomId);

        if (ownerEmail == null || !ownerEmail.equals(requesterEmail)) {
            logger.warn("Yetkisiz oyları gösterme denemesi. Oda Sahibi E-postası: {}, İstek Yapan: {}", ownerEmail,
                    requesterEmail);
            return;
        }
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/reveal", Map.of("reveal", true));
    }

    @MessageMapping("/room/{roomId}/new-round")
    public void newRound(@DestinationVariable String roomId, Principal principal) {
        String requesterEmail = principal.getName();
        String ownerEmail = roomService.getRoomOwnerEmail(roomId);

        if (ownerEmail == null || !ownerEmail.equals(requesterEmail)) {
            logger.warn("Yetkisiz yeni tur başlatma denemesi. Oda Sahibi E-postası: {}, İstek Yapan: {}", ownerEmail,
                    requesterEmail);
            return;
        }
        roomService.startNewRound(roomId);
        publishFullRoomState(roomId);
    }

    @MessageMapping("/room/{roomId}/kick")
    public void kickUser(@DestinationVariable String roomId, @Payload Message kickMessage, Principal principal) {
        String requesterEmail = principal.getName();
        String userToKick = kickMessage.getContent();

        String ownerEmail = roomService.getRoomOwnerEmail(roomId);
        if (ownerEmail == null || !ownerEmail.equals(requesterEmail)) {
            logger.warn("Yetkisiz kullanıcı atma denemesi. Oda Sahibi E-postası: {}, İstek Yapan: {}", ownerEmail,
                    requesterEmail);
            return;
        }

        logger.info("Oda sahibi (e-posta: {}), '{}' kullanıcısını {} odasından atıyor.", requesterEmail, userToKick,
                roomId);
        roomService.kickUserFromRoom(roomId, userToKick);

        publishFullRoomState(roomId);
    }

    @MessageMapping("/room/{roomId}/cancel-voting")
    public void cancelVoting(@DestinationVariable String roomId, Principal principal) {
        String requesterEmail = principal.getName();
        String ownerEmail = roomService.getRoomOwnerEmail(roomId);

        if (ownerEmail == null || !ownerEmail.equals(requesterEmail)) {
            logger.warn("Yetkisiz oylama iptal etme denemesi...");
            return;
        }

        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/notification",
                Map.of("message", "Oylama moderatör tarafından iptal edildi."));

        roomService.cancelVoting(roomId);
        publishFullRoomState(roomId);
    }

    public void publishFullRoomState(String roomId) {
        RoomState currentRoomState = new RoomState();
        currentRoomState.setOwnerEmail(roomService.getRoomOwnerEmail(roomId));
        currentRoomState.setParticipants(roomService.getParticipantsWithAvatars(roomId));
        currentRoomState.setActiveParticipants(roomService.getActiveParticipants(roomId));
        currentRoomState.setActiveTask(roomService.getActiveTask(roomId));
        currentRoomState
                .setVotes(roomService.getVotes(roomId) != null ? roomService.getVotes(roomId) : Collections.emptyMap());
        currentRoomState.setAreVotesRevealed(false);
        currentRoomState.setAiReasoning(roomService.getAIReasoning(roomId));

        currentRoomState.setVotingStartTime(roomService.getVotingStartTime(roomId));

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/state", currentRoomState);
    }
}