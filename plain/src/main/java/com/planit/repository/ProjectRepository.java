package com.planit.repository;

import com.planit.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Belirli bir kullanıcıya ait tüm projeleri bulur
    List<Project> findByUserId(Long userId);

    // Belirli bir kullanıcının, belirli bir GitHub URL'sine sahip projesi olup olmadığını kontrol eder
    boolean existsByUserIdAndGithubUrl(Long userId, String githubUrl);

}