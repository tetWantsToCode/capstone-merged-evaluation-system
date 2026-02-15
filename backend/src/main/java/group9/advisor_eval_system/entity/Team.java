package group9.advisor_eval_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.stream.Collectors;

@Entity
@Table(name = "teams")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Team {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false)
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // Expose class ID without exposing full class object
    @JsonProperty("classId")
    public Long getClassId() {
        return schoolClass != null ? schoolClass.getId() : null;
    }
    
    // Allow setting class by ID during deserialization
    @JsonProperty("classId")
    public void setClassId(Long classId) {
        if (classId != null) {
            SchoolClass tempClass = new SchoolClass();
            tempClass.setId(classId);
            this.schoolClass = tempClass;
        }
    }
    
    // Expose member IDs without exposing full member objects
    @JsonProperty("memberIds")
    public List<Long> getMemberIds() {
        return members != null ? members.stream()
                .map(Student::getId)
                .collect(Collectors.toList()) : new ArrayList<>();
    }
    
    // Allow setting members by IDs during deserialization
    @JsonProperty("memberIds")
    public void setMemberIds(List<Long> memberIds) {
        if (memberIds != null && !memberIds.isEmpty()) {
            this.members = memberIds.stream()
                    .map(id -> {
                        Student student = new Student();
                        student.setId(id);
                        return student;
                    })
                    .collect(Collectors.toList());
        } else {
            this.members = new ArrayList<>();
        }
    }
    
    // Expose adviser IDs without exposing full adviser objects
    @JsonProperty("adviserIds")
    public List<Long> getAdviserIds() {
        return advisers != null ? advisers.stream()
                .map(User::getId)
                .collect(Collectors.toList()) : new ArrayList<>();
    }
    
    // Allow setting advisers by IDs during deserialization
    @JsonProperty("adviserIds")
    public void setAdviserIds(List<Long> adviserIds) {
        if (adviserIds != null && !adviserIds.isEmpty()) {
            this.advisers = adviserIds.stream()
                    .map(id -> {
                        User user = new User();
                        user.setId(id);
                        return user;
                    })
                    .collect(Collectors.toList());
        } else {
            this.advisers = new ArrayList<>();
        }
    }
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    @JsonIgnoreProperties({"students", "teams", "teacher"})
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private SchoolClass schoolClass;
    
    @ManyToMany(mappedBy = "teams")
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<Student> members = new ArrayList<>();
    
    @ManyToMany
    @JoinTable(
        name = "team_advisers",
        joinColumns = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "adviser_id")
    )
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<User> advisers = new ArrayList<>();
    
    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<Evaluation> evaluations = new ArrayList<>();
    
    @ManyToMany
    @JoinTable(
        name = "team_questionnaires",
        joinColumns = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "questionnaire_id")
    )
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<Questionnaire> questionnaires = new ArrayList<>();
}