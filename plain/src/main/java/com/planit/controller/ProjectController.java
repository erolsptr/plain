package com.planit.controller;

import com.planit.model.Project;
import com.planit.model.dto.JiraBulkRequest;
import com.planit.model.dto.JiraBulkResponse;
import com.planit.service.JiraService;
import com.planit.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;
    
    @Autowired
    private JiraService jiraService;

    @GetMapping
    public ResponseEntity<List<Project>> getUserProjects(Authentication authentication) {
        String userEmail = authentication.getName();
        List<Project> projects = projectService.findProjectsByUserEmail(userEmail);
        return ResponseEntity.ok(projects);
    }

    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Map<String, String> payload, Authentication authentication) {
        try {
            String userEmail = authentication.getName();
            String projectName = payload.get("name");
            String githubUrl = payload.get("githubUrl");
            Project createdProject = projectService.createProject(projectName, githubUrl, userEmail);
            return new ResponseEntity<>(createdProject, HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId, Authentication authentication) {
        String userEmail = authentication.getName();
        projectService.deleteProject(projectId, userEmail);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{projectId}/index")
    public ResponseEntity<Project> startIndexing(@PathVariable Long projectId, Authentication authentication) {
        String userEmail = authentication.getName();
        Project updatedProject = projectService.startIndexing(projectId, userEmail);
        return ResponseEntity.accepted().body(updatedProject);
    }

    @PostMapping("/send-bulk-to-jira")
    public ResponseEntity<JiraBulkResponse> sendBulkToJira(
            @RequestBody JiraBulkRequest bulkRequest,
            Authentication authentication) {
        
        String userEmail = authentication.getName();
        JiraBulkResponse response = jiraService.createBulkIssues(bulkRequest, userEmail);
        return ResponseEntity.ok(response);
    }
}