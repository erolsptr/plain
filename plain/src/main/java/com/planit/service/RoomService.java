package com.planit.service;

import com.planit.model.AIVote;
import com.planit.model.PokerRoom;
import com.planit.model.Task;
import com.planit.model.User;
import com.planit.model.Vote;
import com.planit.model.VoteData;
import com.planit.model.dto.TaskCreationRequest;
import com.planit.repository.AIVoteRepository;
import com.planit.repository.PokerRoomRepository;
import com.planit.repository.TaskRepository;
import com.planit.repository.UserRepository;
import com.planit.repository.VoteRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final PokerRoomRepository pokerRoomRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final VoteRepository voteRepository;
    private final AIVoteRepository aiVoteRepository;
    private final RestTemplate restTemplate;

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    private static final String AI_PARTICIPANT_NAME = "plAIn Asistanı";
    private static final String AI_API_URL = "http://localhost:5001/estimate";

    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();
    private final Map<String, Task> activeTasks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, VoteData>> roomVotes = new ConcurrentHashMap<>();
    private final Map<String, String> roomOwnerEmails = new ConcurrentHashMap<>();
    private final Map<String, String> aiReasonings = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> activeUsersByRoom = new ConcurrentHashMap<>();
    private final Map<String, Long> votingStartTimes = new ConcurrentHashMap<>();

    @Transactional
    public void addUserToRoom(String roomId, String username) {
        synchronized (this) {
    if (!rooms.containsKey(roomId)) {
        logger.info("Oda hafızada bulunamadı (ID: {}), veritabanından yeniden canlandırılıyor...", roomId);
        pokerRoomRepository.findById(roomId).ifPresent(room -> {
            Set<String> participants = room.getParticipants().stream()
                    .map(User::getName)
                    .collect(Collectors.toSet());
            participants.add(AI_PARTICIPANT_NAME);
            rooms.put(roomId, participants);

            if (room.getOwner() != null) {
                roomOwnerEmails.put(roomId, room.getOwner().getEmail());
                logger.info("Oda sahibi ({}) {} ID'li oda için hafızaya alındı.", room.getOwner().getEmail(), roomId);
            }
            
            logger.info("Oda (ID: {}) {} katılımcı ile yeniden canlandırıldı.", roomId, participants.size());
        });
    }
}
        
        rooms.computeIfAbsent(roomId, k -> new HashSet<>()).add(username);
        activeUsersByRoom.computeIfAbsent(roomId, k -> new HashSet<>()).add(username);

        PokerRoom room = pokerRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı eklenecek oda bulunamadı: " + roomId));
        User userToJoin = userRepository.findByName(username)
                .orElseThrow(() -> new RuntimeException("Odaya katılacak kullanıcı bulunamadı: " + username));
        if (room.getParticipants().stream().noneMatch(p -> p.getId().equals(userToJoin.getId()))) {
            room.addParticipant(userToJoin);
        }
    }
    
    @Transactional
public void kickUserFromRoom(String roomId, String usernameToKick) {
    Set<String> participantsInMemory = rooms.get(roomId);
    if (participantsInMemory != null) {
        participantsInMemory.remove(usernameToKick);
    }
    Set<String> activeParticipants = activeUsersByRoom.get(roomId);
    if (activeParticipants != null) {
        activeParticipants.remove(usernameToKick);
    }

    
    Map<String, VoteData> votesInMemory = roomVotes.get(roomId);
    if (votesInMemory != null) {
        votesInMemory.remove(usernameToKick);
    }

    PokerRoom room = pokerRoomRepository.findById(roomId)
        .orElseThrow(() -> new RuntimeException("Kullanıcı atılacak oda bulunamadı: " + roomId));
    User userToKick = userRepository.findByName(usernameToKick)
        .orElseThrow(() -> new RuntimeException("Atılacak kullanıcı bulunamadı: " + usernameToKick));
        
    room.removeParticipant(userToKick);
    pokerRoomRepository.save(room);
}

    public void removeUserFromRoom(String roomId, String username) {
    logger.info("'{}' kullanıcısı {} odasından ayrıldı (bağlantı koptu).", username, roomId);
    Set<String> activeParticipants = activeUsersByRoom.get(roomId);
    if (activeParticipants != null) {
        activeParticipants.remove(username);
    }
    
    Map<String, VoteData> votesInMemory = roomVotes.get(roomId);
    if (votesInMemory != null) {
        votesInMemory.remove(username);
    }
}

    public Set<String> getUsersInRoom(String roomId) {
        return rooms.getOrDefault(roomId, Collections.emptySet());
    }

    public Set<String> getActiveParticipants(String roomId) {
        Set<String> active = new HashSet<>(activeUsersByRoom.getOrDefault(roomId, Collections.emptySet()));
        active.add(AI_PARTICIPANT_NAME);
        return active;
    }

    @Transactional
    public Map<String, Map<String, String>> getParticipantsWithAvatars(String roomId) {
        Set<String> participantNames = rooms.getOrDefault(roomId, Collections.emptySet());
        if (participantNames.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Set<String> humanNames = participantNames.stream()
                .filter(name -> !name.equals(AI_PARTICIPANT_NAME))
                .collect(Collectors.toSet());
        
        Map<String, Map<String, String>> participantsMap = new HashMap<>();
        if (!humanNames.isEmpty()) {
            participantsMap = userRepository.findByNameIn(humanNames).stream()
                .collect(Collectors.toMap(
                    User::getName, 
                    user -> Map.of(
                        "avatarId", user.getAvatarId(),
                        "email", user.getEmail()
                    )
                ));
        }
        
        participantsMap.put(AI_PARTICIPANT_NAME, Map.of(
            "avatarId", "bot",
            "email", "ai@plain.com"
        ));
        
        return participantsMap;
    }

    public Task getActiveTask(String roomId) {
        return activeTasks.get(roomId);
    }

    public Long getVotingStartTime(String roomId) {
        return votingStartTimes.get(roomId);
    }

    public void recordVote(String roomId, String username, String vote, Long durationMs) {
    VoteData voteData = new VoteData(vote, durationMs);
    roomVotes.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(username.trim(), voteData);
}

    public void recordAIVote(String roomId, String voterName, String voteValue, String reasoning) {
        recordVote(roomId, voterName, voteValue, null); // AI için süre null olabilir
        if (reasoning != null && !reasoning.isEmpty()) {
            aiReasonings.put(roomId, reasoning);
        }
    }

    public Map<String, VoteData> getVotes(String roomId) {
    return roomVotes.get(roomId);
}
    
    public String getAIReasoning(String roomId) {
        return aiReasonings.get(roomId);
    }

    public void clearAllVotes(String roomId) {
        if (roomVotes.containsKey(roomId)) {
            roomVotes.get(roomId).clear();
        }
        aiReasonings.remove(roomId);
        votingStartTimes.remove(roomId);
    }
    
    private void clearHumanVotes(String roomId) {
    Map<String, VoteData> votes = roomVotes.get(roomId);
    if (votes != null) {
        votes.entrySet().removeIf(entry -> !entry.getKey().equals(AI_PARTICIPANT_NAME));
    }
}

    @Transactional
    public String getRoomOwnerEmail(String roomId) {
        String ownerEmail = roomOwnerEmails.get(roomId);
        if (ownerEmail != null) {
            return ownerEmail;
        }

        return pokerRoomRepository.findById(roomId)
            .map(room -> {
                String email = room.getOwner().getEmail();
                roomOwnerEmails.put(roomId, email);
                return email;
            })
            .orElse(null);
    }

    @Transactional
    public List<Map<String, Object>> getTaskHistoryForRoom(String roomId, String requesterEmail) {
        PokerRoom room = pokerRoomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Oda bulunamadı: " + roomId));
        User requester = userRepository.findByEmail(requesterEmail).orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + requesterEmail));
        if (!room.getParticipants().stream().anyMatch(p -> p.getId().equals(requester.getId()))) { throw new AccessDeniedException("Bu odanın geçmişini görme yetkiniz yok."); }
        List<Task> tasks = room.getTasks().stream()
                .filter(task -> (task.getVotes() != null && !task.getVotes().isEmpty()) || aiVoteRepository.findByTaskId(task.getId()).isPresent())
                .collect(Collectors.toList());
        return tasks.stream().map(task -> {
            Map<String, String> humanVotes = task.getVotes().stream().collect(Collectors.toMap(vote -> vote.getUser().getName(), Vote::getVoteValue));
            Optional<AIVote> aiVoteOpt = aiVoteRepository.findByTaskId(task.getId());
            Map<String, String> allVotes = new HashMap<>(humanVotes);
            aiVoteOpt.ifPresent(aiVote -> allVotes.put(AI_PARTICIPANT_NAME, aiVote.getVoteValue()));
            Map<String, Long> voteCounts = allVotes.values().stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            String consensusScore = voteCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("N/A");
            Long maxVoteId = task.getVotes().stream().map(Vote::getId).max(Comparator.naturalOrder()).orElseGet(() -> aiVoteOpt.map(AIVote::getId).orElse(0L));
            Map<String, Object> returnedMap = new HashMap<>();
            returnedMap.put("taskId", task.getId());
            returnedMap.put("title", task.getTitle());
            returnedMap.put("description", task.getDescription());
            returnedMap.put("consensusScore", consensusScore);
            returnedMap.put("completionOrder", maxVoteId);
            returnedMap.put("votes", allVotes);
            aiVoteOpt.ifPresent(aiVote -> returnedMap.put("aiReasoning", aiVote.getReasoning()));
            return returnedMap;
        }).sorted(Comparator.comparing((Map<String, Object> m) -> (Long)m.get("completionOrder")).reversed()).collect(Collectors.toList());
    }

@Transactional
public void saveCurrentVotingResult(String roomId, String requesterEmail) {
    User requester = userRepository.findByEmail(requesterEmail)
            .orElseThrow(() -> new RuntimeException("İsteği yapan kullanıcı bulunamadı: " + requesterEmail));

    PokerRoom room = pokerRoomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("Sonuçların kaydedileceği oda bulunamadı: " + roomId));

    if (room.getOwner() == null || !room.getOwner().getEmail().equals(requesterEmail)) {
        throw new AccessDeniedException("Sadece oda sahibi sonuçları kaydedebilir.");
    }
    
    Task currentTask = getActiveTask(roomId);
    Map<String, VoteData> currentVotes = getVotes(roomId);
    
    if (currentTask == null || currentTask.getId() == null || currentVotes == null || currentVotes.isEmpty()) {
        activeTasks.remove(roomId);
        clearAllVotes(roomId);
        return;
    }

    List<Vote> humanVotesToSave = new ArrayList<>();
    for (Map.Entry<String, VoteData> entry : currentVotes.entrySet()) {
        String userName = entry.getKey();
        VoteData voteData = entry.getValue();
        String voteValue = voteData.getVoteValue();

        if (userName.equals(AI_PARTICIPANT_NAME)) {
            AIVote aiVote = new AIVote();
            aiVote.setVoteValue(voteValue);
            aiVote.setReasoning(getAIReasoning(roomId));
            aiVote.setTask(currentTask);
            aiVoteRepository.save(aiVote);
        } else {
            User voter = userRepository.findByName(userName).orElseThrow(() -> new RuntimeException("DB'de '" + userName + "' adında kullanıcı bulunamadı."));
            Vote vote = new Vote();
            vote.setUser(voter);
            vote.setVoteValue(voteValue);
            vote.setTask(currentTask);
            humanVotesToSave.add(vote);
        }
    }
    if (!humanVotesToSave.isEmpty()) {
        voteRepository.saveAll(humanVotesToSave);
    }
    activeTasks.remove(roomId);
    clearAllVotes(roomId);
}
    
    @Transactional
    public void deleteRoom(String roomId, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail).orElseThrow(() -> new RuntimeException("İsteği yapan kullanıcı bulunamadı: " + requesterEmail));
        PokerRoom roomToDelete = pokerRoomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Silinecek oda bulunamadı: " + roomId));
        if (!requester.getEmail().equals(roomToDelete.getOwner().getEmail())) {
            throw new AccessDeniedException("Bu odayı silme yetkiniz yok.");
        }

        List<Task> tasksInRoom = roomToDelete.getTasks();
        for(Task task : tasksInRoom) {
            aiVoteRepository.findByTaskId(task.getId()).ifPresent(aiVoteRepository::delete);
        }
        pokerRoomRepository.deleteById(roomId);
        
        rooms.remove(roomId);
        activeUsersByRoom.remove(roomId);
        activeTasks.remove(roomId);
        roomVotes.remove(roomId);
        roomOwnerEmails.remove(roomId);
        aiReasonings.remove(roomId);
        votingStartTimes.remove(roomId);
    }

    @Transactional
    public void createRoom(String roomId, String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
            .orElseThrow(() -> new RuntimeException("Oda sahibi kullanıcı bulunamadı: " + ownerEmail));
        
        PokerRoom newRoom = new PokerRoom();
        newRoom.setId(roomId);
        newRoom.setOwner(owner);
        newRoom.addParticipant(owner);
        pokerRoomRepository.save(newRoom);
        
        roomOwnerEmails.put(roomId, owner.getEmail());
    }

    @Transactional
    public Task createTask(String roomId, TaskCreationRequest taskRequest, String requesterEmail) {
        PokerRoom room = pokerRoomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Görev eklenecek oda bulunamadı: " + roomId));
        User requester = userRepository.findByEmail(requesterEmail).orElseThrow(() -> new RuntimeException("İsteği yapan kullanıcı bulunamadı: " + requesterEmail));
        if (!room.getOwner().getId().equals(requester.getId())) {
            throw new AccessDeniedException("Sadece oda sahibi yeni görev oluşturabilir.");
        }
        Task newTask = new Task();
        newTask.setTitle(taskRequest.getTitle());
        newTask.setDescription(taskRequest.getDescription());
        newTask.setCardSet(taskRequest.getCardSet());
        newTask.setPokerRoom(room);
        return taskRepository.save(newTask);
    }

    @Transactional
    public List<Task> getPendingTasksForRoom(String roomId, String requesterEmail) {
        PokerRoom room = pokerRoomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Oda bulunamadı: " + roomId));
        User requester = userRepository.findByEmail(requesterEmail).orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + requesterEmail));
        boolean isParticipant = room.getParticipants().stream().anyMatch(p -> p.getId().equals( requester.getId()));
        if (!isParticipant) { throw new AccessDeniedException("Bu odanın görevlerini görme yetkiniz yok."); }
        List<Task> allTasks = taskRepository.findByPokerRoomId(roomId);
        List<Task> pendingTasks = new ArrayList<>();
        for (Task task : allTasks) {
            long humanVoteCount = voteRepository.countByTaskId(task.getId());
            boolean hasAiVote = aiVoteRepository.findByTaskId(task.getId()).isPresent();
            if (humanVoteCount == 0 && !hasAiVote) {
                pendingTasks.add(task);
            }
        }
        return pendingTasks;
    }

        @Transactional
    public void setActiveTask(String roomId, Task taskWithProjectId, String requesterEmail) {
        PokerRoom room = pokerRoomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Görev eklenecek oda veritabanında bulunamadı: " + roomId));
        
        // Frontend'den gelen Task nesnesinin projectId'sini al
        Long projectId = taskWithProjectId.getProjectId();
        
        // Veritabanı işlemleri için projectId'si olmayan temiz bir Task nesnesi kullanalım
        taskWithProjectId.setPokerRoom(room);
        Task taskToActivate = (taskWithProjectId.getId() == null) ? taskRepository.save(taskWithProjectId) : taskWithProjectId;
        
        activeTasks.put(roomId, taskToActivate); 
        clearAllVotes(roomId);
        
        votingStartTimes.put(roomId, System.currentTimeMillis());
        
        // triggerAIEstimation metoduna projectId'yi de gönder
        triggerAIEstimation(roomId, taskToActivate, projectId, requesterEmail);
    }

        private void triggerAIEstimation(String roomId, Task task, Long projectId, String requesterEmail) {
        Set<String> participants = rooms.get(roomId);
        if (participants == null || !participants.contains(AI_PARTICIPANT_NAME)) {
            logger.info("AI participant '{}' is not in room {}. Skipping estimation.", AI_PARTICIPANT_NAME, roomId);
            return;
        }

        // Proje ID'si seçilmemişse, kod analizi yapmadan devam et
        if (projectId == null) {
            logger.info("Kod analizi için proje seçilmedi. Standart tahminleme yapılıyor.");
        }

        logger.info("Oylama geçmişi alınıyor ve AI için hazırlanıyor...");
        List<Map<String, Object>> fullHistory = getTaskHistoryForRoom(roomId, requesterEmail);
        List<Map<String, String>> simplifiedHistory = fullHistory.stream()
            .map(historyItem -> Map.of(
                "title", (String) historyItem.get("title"),
                "description", (String) historyItem.getOrDefault("description", ""),
                "consensusScore", (String) historyItem.get("consensusScore")
            ))
            .collect(Collectors.toList());
        final int HISTORY_LIMIT = 15;
        if (simplifiedHistory.size() > HISTORY_LIMIT) {
            simplifiedHistory = simplifiedHistory.subList(0, HISTORY_LIMIT);
        }

        logger.info("Triggering AI estimation for task: {} in room: {}. Including {} history items. Project Context ID: {}", task.getId(), roomId, simplifiedHistory.size(), projectId);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomId", roomId);
        payload.put("taskId", task.getId());
        payload.put("title", task.getTitle());
        payload.put("description", task.getDescription());
        payload.put("cardSet", task.getCardSet().split(","));
        payload.put("taskHistory", simplifiedHistory);
        payload.put("projectId", projectId); 

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        try {
            restTemplate.postForLocation(AI_API_URL, entity);
            logger.info("Successfully sent estimation request to AI service for task: {}", task.getId());
        } catch (RestClientException e) {
            logger.error("Error sending estimation request to AI service for task: {}. Error: {}", task.getId(), e.getMessage());
        }
    }

   @Transactional
    public Set<Map<String, String>> findRoomsByUserEmail(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));
        Long userId = user.getId();
        Set<PokerRoom> userRooms = pokerRoomRepository.findRoomsByParticipantId(userId);
        return userRooms.stream()
                .map(room -> {
                    String ownerName = (room.getOwner() != null) ? room.getOwner().getName() : "Bilinmiyor";
                    return Map.of(
                            "roomId", room.getId(),
                            "ownerName", ownerName,
                            "taskCount", String.valueOf(room.getTasks().size())
                    );
                })
                .collect(Collectors.toSet());
    }

    @Transactional
    public void startNewRound(String roomId) {
        clearHumanVotes(roomId);
        
        votingStartTimes.put(roomId, System.currentTimeMillis());
        
        Task currentTask = getActiveTask(roomId);
        if (currentTask != null) {
            PokerRoom room = pokerRoomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Oda veritabanında bulunamadı: " + roomId));
            Task taskToReset = room.getTasks().stream()
                .filter(t -> t.getId().equals(currentTask.getId()))
                .findFirst().orElse(null);
            if (taskToReset != null && !taskToReset.getVotes().isEmpty()) {
                voteRepository.deleteAll(taskToReset.getVotes());
                taskToReset.getVotes().clear();
            }
        }
    }
}