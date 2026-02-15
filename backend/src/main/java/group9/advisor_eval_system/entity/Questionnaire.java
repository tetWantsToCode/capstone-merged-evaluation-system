package group9.advisor_eval_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "questionnaires")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Questionnaire {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false)
    private String title;
    
    @Column(length = 2000)
    private String description;
    
    @Column(nullable = false, unique = true)
    private String googleFormId; // Google Forms API ID
    
    @Column(nullable = true)
    private String googleFormUrl;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_teacher_id", nullable = false)
    private User createdByTeacher;
    
    @ManyToMany
    @JoinTable(
        name = "class_questionnaires",
        joinColumns = @JoinColumn(name = "questionnaire_id"),
        inverseJoinColumns = @JoinColumn(name = "class_id")
    )
    private Set<SchoolClass> assignedClasses = new HashSet<>();
    
    @ManyToMany(mappedBy = "questionnaires")
    private Set<Team> assignedTeams = new HashSet<>();
    
    @OneToMany(mappedBy = "questionnaire", cascade = CascadeType.ALL)
    private Set<QuestionnaireItem> items = new HashSet<>();
}