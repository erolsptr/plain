package com.planit.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class JiraBulkResponse {
    private int successCount;
    private int failureCount;
    private List<String> createdIssueKeys;
    private List<String> failedTaskTitles;
}