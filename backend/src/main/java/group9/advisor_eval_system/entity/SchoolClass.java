package group9.advisor_eval_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "classes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchoolClass {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = true)
    private String section;
    
    @NotBlank
    @Column(nullable = false)
    private String schoolYear; // e.g., "2024-2025"
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // Expose teacher ID for filtering without exposing full teacher object
    @JsonProperty("teacherId")
    public Long getTeacherId() {
        return teacher != null ? teacher.getId() : null;
    }
    
    // Allow setting teacher by ID during deserialization
    @JsonProperty("teacherId")
    public void setTeacherId(Long teacherId) {
        if (teacherId != null) {
            User tempTeacher = new User();
            tempTeacher.setId(teacherId);
            this.teacher = tempTeacher;
        }
    }
    
    // Expose team IDs without exposing full team objects
    @JsonProperty("teamIds")
    public List<Long> getTeamIds() {
        return teams != null ? teams.stream()
                .map(Team::getId)
                .collect(java.util.stream.Collectors.toList()) : new ArrayList<>();
    }
    
    // Relationships
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id", nullable = false)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private User teacher;
    
    @ManyToMany(mappedBy = "classes")
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<Student> students = new ArrayList<>();
    
    @OneToMany(mappedBy = "schoolClass", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<Team> teams = new ArrayList<>();
}