package com.planit.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class JiraBulkRequest {
    private List<JiraTaskData> tasks;
}