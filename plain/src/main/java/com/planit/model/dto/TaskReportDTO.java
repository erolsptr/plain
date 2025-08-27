package com.planit.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class TaskReportDTO {
    private String title;
    private String consensusScore;
    private List<VoteDetailDTO> votes;
}