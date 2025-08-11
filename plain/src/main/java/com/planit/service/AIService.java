package com.planit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planit.model.Task;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class AIService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Getter @Setter
    public static class AIResponse {
        private String finalVote;
        private String justification;
        private List<BreakdownItem> breakdown;
    }
    @Getter @Setter
    public static class BreakdownItem {
        private String subTask;
        private String points;
        private String reason;
    }

    public AIResponse getAIEstimate(Task task) {
        String aiServiceUrl = "http://localhost:5001/estimate";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Task> entity = new HttpEntity<>(task, headers);

            AIResponse response = restTemplate.postForObject(aiServiceUrl, entity, AIResponse.class);
            
            return response;

        } catch (Exception e) {
            System.err.println("Python AI servisine bağlanırken hata oluştu: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}