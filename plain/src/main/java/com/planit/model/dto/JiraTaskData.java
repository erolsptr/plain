package com.planit.model.dto;

import lombok.Data;

@Data
public class JiraTaskData {
    private Long taskId;
    private String consensusScore;
}