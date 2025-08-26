package com.planit.service;

import com.planit.model.Task;
import com.planit.model.User;
import com.planit.model.dto.JiraBulkRequest;
import com.planit.model.dto.JiraBulkResponse;
import com.planit.model.dto.JiraTaskData;
import com.planit.repository.TaskRepository;
import com.planit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JiraService {

    private static final Logger logger = LoggerFactory.getLogger(JiraService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private RestTemplate restTemplate;

    public JiraBulkResponse createBulkIssues(JiraBulkRequest bulkRequest, String userEmail) {
        int successCount = 0;
        int failureCount = 0;
        List<String> createdIssueKeys = new ArrayList<>();
        List<String> failedTaskTitles = new ArrayList<>();

        for (JiraTaskData taskData : bulkRequest.getTasks()) {
            try {
                String issueKey = createJiraIssue(taskData.getTaskId(), taskData.getConsensusScore(), userEmail);
                successCount++;
                createdIssueKeys.add(issueKey);
            } catch (Exception e) {
                logger.error("Toplu gönderim sırasında görev ID {} gönderilemedi: {}", taskData.getTaskId(),
                        e.getMessage());
                failureCount++;
                // Görevin başlığını bulup hata listesine ekleyelim
                taskRepository.findById(taskData.getTaskId())
                        .ifPresent(task -> failedTaskTitles.add(task.getTitle()));
            }
        }

        return new JiraBulkResponse(successCount, failureCount, createdIssueKeys, failedTaskTitles);
    }

    public String createJiraIssue(Long taskId, String consensusScore, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + userEmail));

        String jiraUrl = user.getJiraUrl();
        String jiraUserEmail = user.getJiraEmail();
        String apiToken = user.getJiraApiToken();
        String projectKey = user.getJiraProjectKey();
        Double pointHourRatio = user.getJiraPointHourRatio();

        if (jiraUrl == null || jiraUserEmail == null || apiToken == null || projectKey == null) {
            throw new IllegalStateException("Kullanıcının Jira entegrasyon bilgileri eksik.");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Jira'ya gönderilecek görev bulunamadı: " + taskId));

        String baseUrl = jiraUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String createIssueUrl = baseUrl + "/rest/api/3/issue";

        String auth = jiraUserEmail + ":" + apiToken;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");

        Double storyPointsValue = null;
        String originalEstimateValue = null;
        try {
            storyPointsValue = Double.parseDouble(consensusScore.replace("½", "0.5"));

            if (pointHourRatio != null && pointHourRatio > 0) {
                double hours = storyPointsValue * pointHourRatio;
                originalEstimateValue = Math.round(hours) + "h";
            }
        } catch (NumberFormatException e) {
            logger.warn(
                    "Sayısal olmayan consensusScore ('{}') için Story Points veya Original Estimate hesaplanamıyor.",
                    consensusScore);
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("summary", task.getTitle());
        String descriptionText = String.format(
                "%s\n\n---\n*plAIn Oylama Sonucu: %s*",
                task.getDescription() != null ? task.getDescription() : "",
                consensusScore);
        fields.put("description", Map.of(
                "type", "doc", "version", 1,
                "content", new Object[] {
                        Map.of("type", "paragraph", "content", new Object[] {
                                Map.of("type", "text", "text", descriptionText)
                        })
                }));
        fields.put("issuetype", Map.of("name", "Task"));

        if (storyPointsValue != null) {
            String storyPointsFieldId = "customfield_10016";
            fields.put(storyPointsFieldId, storyPointsValue);
        }
        if (originalEstimateValue != null) {
            fields.put("timetracking", Map.of("originalEstimate", originalEstimateValue));
        }

        Map<String, Object> issueDetails = Map.of("fields", fields);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(issueDetails, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(createIssueUrl, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                String issueKey = (String) response.getBody().get("key");
                logger.info("Görev başarıyla Jira'ya gönderildi. Issue Key: {}", issueKey);
                return issueKey;
            } else {
                throw new RuntimeException(
                        "Jira'ya görev oluşturulamadı, beklenmedik yanıt: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                logger.error("Jira API Hatası: 401 Unauthorized. API Token veya Jira E-postası yanlış olabilir.");
                throw new RuntimeException(
                        "Jira kimlik doğrulaması başarısız oldu. Lütfen Profil sayfasındaki API Token ve Jira E-posta bilgilerinizi kontrol edin.");
            }
            logger.error("Jira API hatası: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Jira API'sine bağlanırken bir hata oluştu: " + e.getResponseBodyAsString());
        }
    }
}