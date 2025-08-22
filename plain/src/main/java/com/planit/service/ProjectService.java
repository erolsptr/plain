package com.planit.service;

import com.planit.model.Project;
import com.planit.model.User;
import com.planit.repository.ProjectRepository;
import com.planit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class ProjectService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Project> findProjectsByUserEmail(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + userEmail));
        return projectRepository.findByUserId(user.getId());
    }

    @Transactional
    public Project createProject(String projectName, String githubUrl, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + userEmail));
        
        if (projectRepository.existsByUserIdAndGithubUrl(user.getId(), githubUrl)) {
            throw new IllegalStateException("Bu GitHub projesi zaten eklenmiş.");
        }

        Project newProject = new Project();
        newProject.setName(projectName);
        newProject.setGithubUrl(githubUrl);
        newProject.setUser(user);
        
        return projectRepository.save(newProject);
    }

    @Transactional
    public void deleteProject(Long projectId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + userEmail));
        
        Project projectToDelete = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Proje bulunamadı: " + projectId));

        if (!projectToDelete.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Bu projeyi silme yetkiniz yok.");
        }
        
        projectRepository.deleteById(projectId);
    }
    
    @Transactional
    public Project startIndexing(Long projectId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + userEmail));
        
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Proje bulunamadı: " + projectId));

        if (!project.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Bu projeyi indeksleme yetkiniz yok.");
        }

        project.setIndexingStatus("INDEXING");
        project.setIndexingError(null);
        projectRepository.save(project);

        new Thread(() -> {
            try {
                String aiServerPath = new File("../ai-server").getCanonicalPath();
                
                ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    "indexer.py",
                    project.getGithubUrl(),
                    String.valueOf(project.getId())
                );
                
                processBuilder.directory(new File(aiServerPath));
                processBuilder.redirectErrorStream(true);
                
                logger.info("İndeksleme komutu çalıştırılıyor: " + String.join(" ", processBuilder.command()));

                Process process = processBuilder.start();
                
                int exitCode = process.waitFor();
                
                Project finishedProject = projectRepository.findById(projectId).orElse(null);
                if (finishedProject != null) {
                    if (exitCode == 0) {
                        finishedProject.setIndexingStatus("COMPLETED");
                        finishedProject.setLastIndexedAt(LocalDateTime.now());
                        logger.info("Proje {} için indeksleme başarıyla tamamlandı.", projectId);
                    } else {
                        finishedProject.setIndexingStatus("FAILED");
                        finishedProject.setIndexingError("İndeksleme betiği bir hatayla sonlandı. Çıkış kodu: " + exitCode);
                        logger.error("Proje {} için indeksleme başarısız oldu. Çıkış kodu: {}", projectId, exitCode);
                    }
                    projectRepository.save(finishedProject);
                }

            } catch (IOException | InterruptedException e) {
                logger.error("İndeksleme betiği başlatılamadı veya kesintiye uğradı.", e);
                Project failedProject = projectRepository.findById(projectId).orElse(null);
                if (failedProject != null) {
                    failedProject.setIndexingStatus("FAILED");
                    failedProject.setIndexingError("Indexer script could not be started: " + e.getMessage());
                    projectRepository.save(failedProject);
                }
                Thread.currentThread().interrupt();
            }
        }).start();

        return project;
    }
}