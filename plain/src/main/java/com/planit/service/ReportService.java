package com.planit.service;

import com.planit.model.PokerRoom;
import com.planit.model.User;
import com.planit.model.dto.TaskReportDTO;
import com.planit.model.dto.UserReportDTO;
import com.planit.model.dto.VoteDetailDTO;
import com.planit.repository.AIVoteRepository;
import com.planit.repository.PokerRoomRepository;
import com.planit.repository.RoomDetailsRepository;
import com.planit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PokerRoomRepository pokerRoomRepository;
    
    @Autowired
    private RoomDetailsRepository roomDetailsRepository;

    @Autowired
    private AIVoteRepository aiVoteRepository;

    private static final String AI_PARTICIPANT_NAME = "plAIn Asistanı";

    @Transactional(readOnly = true)
    public UserReportDTO generateUserReport(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + userEmail));

        List<PokerRoom> ownedRooms = pokerRoomRepository.findByOwnerId(user.getId());

        UserReportDTO report = new UserReportDTO();
        report.setTotalOwnedRooms(ownedRooms.size());
        
        Map<String, List<TaskReportDTO>> roomReports = new LinkedHashMap<>();
        double totalStoryPoints = 0;
        int totalVotedTasks = 0;

        for (PokerRoom room : ownedRooms) {
            List<TaskReportDTO> taskReports = new ArrayList<>();

            room.getTasks().stream()
                .filter(task -> task.getConsensusScore() != null && !task.getConsensusScore().isEmpty())
                .forEach(task -> {
                    TaskReportDTO taskReport = new TaskReportDTO();
                    taskReport.setTitle(task.getTitle());
                    taskReport.setConsensusScore(task.getConsensusScore());

                    List<VoteDetailDTO> voteDetails = task.getVotes().stream()
                            .map(vote -> new VoteDetailDTO(vote.getUser().getName(), vote.getVoteValue()))
                            .collect(Collectors.toList());

                    aiVoteRepository.findByTaskId(task.getId())
                            .ifPresent(aiVote -> voteDetails.add(new VoteDetailDTO(AI_PARTICIPANT_NAME, aiVote.getVoteValue())));

                    taskReport.setVotes(voteDetails);
                    taskReports.add(taskReport);
                });

            if (!taskReports.isEmpty()) {
                String roomName = roomDetailsRepository.findById(room.getId())
                                .map(rd -> rd.getRoomName())
                                .orElse("İsimsiz Oda: " + room.getId());
                roomReports.put(roomName, taskReports);
                
                totalVotedTasks += taskReports.size();
                totalStoryPoints += taskReports.stream()
                    .mapToDouble(tr -> {
                        try {
                            String score = tr.getConsensusScore().replace("½", "0.5");
                            return Double.parseDouble(score);
                        } catch (NumberFormatException e) {
                            return 0.0; 
                        }
                    }).sum();
            }
        }

        report.setTotalVotedTasks(totalVotedTasks);
        report.setTotalStoryPoints(totalStoryPoints);
        report.setRoomReports(roomReports);

        return report;
    }
}