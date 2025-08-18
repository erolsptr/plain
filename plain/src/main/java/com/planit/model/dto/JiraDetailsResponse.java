package com.planit.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JiraDetailsResponse {
    private String jiraUrl;
    private String jiraEmail;
    private boolean hasApiToken; 
    private String jiraProjectKey;
    private Double jiraPointHourRatio;
}