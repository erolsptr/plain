package com.planit.model.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class UserReportDTO {
    private int totalOwnedRooms;
    private int totalVotedTasks;
    private double totalStoryPoints;

    private Map<String, List<TaskReportDTO>> roomReports;
}