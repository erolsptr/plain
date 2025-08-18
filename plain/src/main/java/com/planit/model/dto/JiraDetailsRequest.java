package com.planit.model.dto;

import lombok.Data;

@Data
public class JiraDetailsRequest {
    private String jiraUrl;
    private String jiraEmail;
    private String jiraApiToken;
    private String jiraProjectKey;
    private Double jiraPointHourRatio;
}