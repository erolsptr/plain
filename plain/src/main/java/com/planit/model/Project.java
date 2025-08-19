package com.planit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "github_url", nullable = false, unique = true, length = 512)
    private String githubUrl;
    
    // Projenin indekslenme durumunu takip etmek için
    // Olası değerler: PENDING, INDEXING, COMPLETED, FAILED
    @Column(name = "indexing_status", nullable = false)
    private String indexingStatus = "PENDING"; 

    @Column(name = "indexing_error", columnDefinition = "TEXT")
    private String indexingError;

    @Column(name = "last_indexed_at")
    private LocalDateTime lastIndexedAt;

    // Bu projenin hangi kullanıcıya ait olduğunu belirtir.
    // Bir kullanıcı silindiğinde, ona ait projeler de silinir (CascadeType.ALL).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore // API üzerinden bu bilgiyi doğrudan göstermeyelim.
    private User user;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return id != null ? id.equals(project.id) : project.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}