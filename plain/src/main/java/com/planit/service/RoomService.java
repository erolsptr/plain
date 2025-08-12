package com.planit.service;

import com.planit.model.AIVote;
import com.planit.model.PokerRoom;
import com.planit.model.Task;
import com.planit.model.User;
import com.planit.model.Vote;
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
    private final Map<String, Map<String, String>> roomVotes = new ConcurrentHashMap<>();
    private final Map<String, String> roomOwners = new ConcurrentHashMap<>();
    private final Map<String, String> aiReasonings = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> activeUsersByRoom = new ConcurrentHashMap<>();

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
        // Hem genel hem de aktif listelerden sil
        Set<String> participantsInMemory = rooms.get(roomId);
        if (participantsInMemory != null) {
            participantsInMemory.remove(usernameToKick);
        }
        Set<String> activeParticipants = activeUsersByRoom.get(roomId);
        if (activeParticipants != null) {
            activeParticipants.remove(usernameToKick);
        }

        Map<String, String> votesInMemory = roomVotes.get(roomId);
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
        
        Map<String, String> votesInMemory = roomVotes.get(roomId);
        if (votesInMemory != null) {
            votesInMemory.remove(username);
        }
    }

    public Set<String> getUsersInRoom(String roomId) {
        return rooms.getOrDefault(roomId, Collections.emptySet());
    }

    public Set<String> getActiveParticipants(String roomId) {
        // AI her zaman aktif kabul edilir
        Set<String> active = new HashSet<>(activeUsersByRoom.getOrDefault(roomId, Collections.emptySet()));
        active.add(AI_PARTICIPANT_NAME);
        return active;
    }

    @Transactional
    public Map<String, String> getParticipantsWithAvatars(String roomId) {
        Set<String> participantNames = rooms.getOrDefault(roomId, Collections.emptySet());
        if (participantNames.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> humanNames = participantNames.stream()
                .filter(name -> !name.equals(AI_PARTICIPANT_NAME))
                .collect(Collectors.toSet());
        Map<String, String> participantsMap = new HashMap<>();
        if (!humanNames.isEmpty()) {
             participantsMap = userRepository.findByNameIn(humanNames).stream()
                .collect(Collectors.toMap(User::getName, User::getAvatarId));
        }
        participantsMap.put(AI_PARTICIPANT_NAME, "bot");
        return participantsMap;
    }

    public Task getActiveTask(String roomId) {
        return activeTasks.get(roomId);
    }

    public void recordVote(String roomId, String username, String vote) {
        roomVotes.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(username.trim(), vote);
    }

    public void recordAIVote(String roomId, String voterName, String voteValue, String reasoning) {
        recordVote(roomId, voterName, voteValue);
        if (reasoning != null && !reasoning.isEmpty()) {
            aiReasonings.put(roomId, reasoning);
        }
    }

    public Map<String, String> getVotes(String roomId) {
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
    }
    
    private void clearHumanVotes(String roomId) {
        Map<String, String> votes = roomVotes.get(roomId);
        if (votes != null) {
            votes.entrySet().removeIf(entry -> !entry.getKey().equals(AI_PARTICIPANT_NAME));
        }
    }

    @Transactional
    public String getRoomOwner(String roomId) {
        String ownerName = roomOwners.get(roomId);
        if (ownerName != null) { return ownerName; }
        PokerRoom room = pokerRoomRepository.findById(roomId).orElse(null);
        if (room != null) {
            ownerName = room.getOwner().getName();
            roomOwners.put(roomId, ownerName);
            return ownerName;
        }
        return null;
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
            Map<String, Long> voteCounts = allVotes.values().stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));
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
        User requester = userRepository.findByEmail(requesterEmail).orElseThrow(() -> new RuntimeException("İsteği yapan kullanıcı bulunamadı: " + requesterEmail));
        String ownerName = getRoomOwner(roomId);
        if (ownerName == null || !requester.getName().equals(ownerName)) { throw new AccessDeniedException("Sadece oda sahibi sonuçları kaydedebilir."); }
        Task currentTask = getActiveTask(roomId);
        Map<String, String> currentVotes = getVotes(roomId);
        if (currentTask == null || currentTask.getId() == null || currentVotes == null || currentVotes.isEmpty()) {
            activeTasks.remove(roomId);
            clearAllVotes(roomId);
            return;
        }
        List<Vote> humanVotesToSave = new ArrayList<>();
        for (Map.Entry<String, String> entry : currentVotes.entrySet()) {
            String userName = entry.getKey();
            String voteValue = entry.getValue();
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
        if (!requester.getName().equals(roomToDelete.getOwner().getName())) { throw new AccessDeniedException("Bu odayı silme yetkiniz yok."); }
        List<Task> tasksInRoom = roomToDelete.getTasks();
        for(Task task : tasksInRoom) {
            aiVoteRepository.findByTaskId(task.getId()).ifPresent(aiVoteRepository::delete);
        }
        pokerRoomRepository.deleteById(roomId);
        rooms.remove(roomId);
        activeUsersByRoom.remove(roomId);
        activeTasks.remove(roomId);
        roomVotes.remove(roomId);
        roomOwners.remove(roomId);
        aiReasonings.remove(roomId);
    }

    @Transactional
public void createRoom(String roomId, String ownerEmail) {
    logger.error("--- createRoom BAŞLADI --- İstenen Sahip E-postası: '{}'", ownerEmail);

    User owner = userRepository.findByEmail(ownerEmail)
            .orElseThrow(() -> {
                logger.error("--- HATA --- findByEmail '{}' ile kullanıcıyı BULAMADI.", ownerEmail);
                return new RuntimeException("Oda sahibi kullanıcı bulunamadı: " + ownerEmail);
            });
    
    logger.error("--- KANIT --- findByEmail '{}' sorgusu, Adı: '{}' olan kullanıcıyı getirdi.", ownerEmail, owner.getName());

    if (!owner.getEmail().equals(ownerEmail)) {
        logger.error("!!!!!! KRİTİK HATA! VERİTABANI SORGUSU YANLIŞ KULLANICIYI GETİRDİ! İstenen: {}, Gelen: {}", ownerEmail, owner.getEmail());
    }

    PokerRoom newRoom = new PokerRoom();
    newRoom.setId(roomId);
    newRoom.setOwner(owner);
    newRoom.addParticipant(owner);
    pokerRoomRepository.save(newRoom);

    logger.error("--- ODA OLUŞTURULDU --- Sahip olarak atanan kullanıcı: '{}'", owner.getName());
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
    public void setActiveTask(String roomId, Task task, String requesterEmail) {
        PokerRoom room = pokerRoomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Görev eklenecek oda veritabanında bulunamadı: " + roomId));
        task.setPokerRoom(room);
        Task taskToActivate = (task.getId() == null) ? taskRepository.save(task) : task;
        activeTasks.put(roomId, taskToActivate); 
        clearAllVotes(roomId);
        triggerAIEstimation(roomId, taskToActivate, requesterEmail);
    }

    private void triggerAIEstimation(String roomId, Task task, String requesterEmail) {
        Set<String> participants = rooms.get(roomId);
        if (participants == null || !participants.contains(AI_PARTICIPANT_NAME)) {
            logger.info("AI participant '{}' is not in room {}. Skipping estimation.", AI_PARTICIPANT_NAME, roomId);
            return;
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
            final int HISTORY_LIMIT = 10; // AI'a gönderilecek maksimum geçmiş görev sayısı
    if (simplifiedHistory.size() > HISTORY_LIMIT) {
        simplifiedHistory = simplifiedHistory.subList(0, HISTORY_LIMIT);
    }
        logger.info("Triggering AI estimation for task: {} in room: {}. Including {} history items.", task.getId(), roomId, simplifiedHistory.size());
        Map<String, Object> payload = new HashMap<>();
        payload.put("roomId", roomId);
        payload.put("taskId", task.getId());
        payload.put("title", task.getTitle());
        payload.put("description", task.getDescription());
        payload.put("cardSet", task.getCardSet().split(","));
        payload.put("taskHistory", simplifiedHistory);
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
    // 1. Önce e-postadan kullanıcının GERÇEK ID'sini bul.
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));
    Long userId = user.getId();

    // 2. Sadece ve sadece bu ID'yi kullanarak odaları bul.
    Set<PokerRoom> userRooms = pokerRoomRepository.findRoomsByParticipantId(userId);

    // 3. Sonucu işle.
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