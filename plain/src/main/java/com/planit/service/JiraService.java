package com.planit.service;

import com.planit.model.Task;
import com.planit.model.User;
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

import java.util.Base64;
import java.util.HashMap;
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


public String createJiraIssue(Long taskId, String consensusScore, String userEmail) {
    // 1. Kullanıcıyı ve tüm Jira bilgilerini al
    User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + userEmail));

    String jiraUrl = user.getJiraUrl();
    String jiraUserEmail = user.getJiraEmail();
    String apiToken = user.getJiraApiToken();
    String projectKey = user.getJiraProjectKey();
    Double pointHourRatio = user.getJiraPointHourRatio(); // Yeni dönüşüm oranını al

    if (jiraUrl == null || jiraUserEmail == null || apiToken == null || projectKey == null) {
        throw new IllegalStateException("Kullanıcının Jira entegrasyon bilgileri eksik.");
    }

    Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Jira'ya gönderilecek görev bulunamadı: " + taskId));

    // URL'yi normalize et (302 hatasını önlemek için)
    String baseUrl = jiraUrl.trim();
    if (baseUrl.endsWith("/")) {
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    String createIssueUrl = baseUrl + "/rest/api/3/issue";

    // 2. Kimlik doğrulama başlığını oluştur
    String auth = jiraUserEmail + ":" + apiToken;
    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Basic " + encodedAuth);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Accept", "application/json");

    // 3. Story Points ve Original Estimate değerlerini hesapla
    Double storyPointsValue = null;
    String originalEstimateValue = null;
    try {
        // "½" gibi değerleri sayıya çevir
        storyPointsValue = Double.parseDouble(consensusScore.replace("½", "0.5"));
        
        // Eğer kullanıcı bir dönüşüm oranı belirttiyse, originalEstimate'i hesapla
        if (pointHourRatio != null && pointHourRatio > 0) {
            double hours = storyPointsValue * pointHourRatio;
            // Jira, dakika bazlı (m) veya hafta bazlı (w) da kabul eder.
            // Şimdilik sadece saat (h) olarak gönderiyoruz.
            originalEstimateValue = Math.round(hours) + "h"; // Örn: "16h"
        }
    } catch (NumberFormatException e) {
        logger.warn("Sayısal olmayan consensusScore ('{}') için Story Points veya Original Estimate hesaplanamıyor.", consensusScore);
    }

    // 4. Gönderilecek JSON'ın "fields" bölümünü dinamik olarak oluştur
    Map<String, Object> fields = new HashMap<>();
    fields.put("project", Map.of("key", projectKey));
    fields.put("summary", task.getTitle());
    // Jira'nın yeni metin formatı için description'ı doğru yapılandıralım
    String descriptionText = String.format(
        "%s\n\n---\n*plAIn Oylama Sonucu: %s*",
        task.getDescription() != null ? task.getDescription() : "",
        consensusScore
    );
    fields.put("description", Map.of(
        "type", "doc", "version", 1,
        "content", new Object[]{
            Map.of("type", "paragraph", "content", new Object[]{
                Map.of("type", "text", "text", descriptionText)
            })
        }
    ));
    fields.put("issuetype", Map.of("name", "Task")); // Veya "Görev" veya projenize uygun olan

    // Sadece hesaplanabildiyse alanları ekle
    if (storyPointsValue != null) {
        String storyPointsFieldId = "customfield_10016"; // KENDİ PROJENİZİN DOĞRU ID'Sİ
        fields.put(storyPointsFieldId, storyPointsValue);
    }
    if (originalEstimateValue != null) {
        fields.put("timetracking", Map.of("originalEstimate", originalEstimateValue));
    }
    
    Map<String, Object> issueDetails = Map.of("fields", fields);

    // 5. API isteğini gönder
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(issueDetails, headers);
    try {
        ResponseEntity<Map> response = restTemplate.postForEntity(createIssueUrl, entity, Map.class);
        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            String issueKey = (String) response.getBody().get("key");
            logger.info("Görev başarıyla Jira'ya gönderildi. Issue Key: {}", issueKey);
            return issueKey;
        } else {
            throw new RuntimeException("Jira'ya görev oluşturulamadı, beklenmedik yanıt: " + response.getStatusCode());
        }
    } catch (HttpClientErrorException e) {
        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            logger.error("Jira API Hatası: 401 Unauthorized. API Token veya Jira E-postası yanlış olabilir.");
            throw new RuntimeException("Jira kimlik doğrulaması başarısız oldu. Lütfen Profil sayfasındaki API Token ve Jira E-posta bilgilerinizi kontrol edin.");
        }
        logger.error("Jira API hatası: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        throw new RuntimeException("Jira API'sine bağlanırken bir hata oluştu: " + e.getResponseBodyAsString());
    }
}
}