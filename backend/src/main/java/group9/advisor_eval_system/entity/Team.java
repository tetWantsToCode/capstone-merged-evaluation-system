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
    @Transient
    @JsonProperty("memberIds")
    public List<Long> getMemberIds() {
        return teamStudents != null ? teamStudents.stream()
                .map(ts -> ts.getStudent().getId())
                .collect(Collectors.toList()) : new ArrayList<>();
    }
    
    // Allow setting members by IDs during deserialization
    @Transient
    @JsonIgnore
    public void setMemberIds(List<Long> memberIds) {
        if (memberIds != null && !memberIds.isEmpty()) {
            // This is a convenience method - in practice, use TeamStudent directly
            if (teamStudents == null) {
                teamStudents = new ArrayList<>();
            }
            teamStudents.clear();
            for (Long memberId : memberIds) {
                TeamStudent ts = new TeamStudent();
                Student student = new Student();
                student.setId(memberId);
                ts.setStudent(student);
                ts.setTeam(this);
                teamStudents.add(ts);
            }
        } else {
            if (teamStudents != null) {
                teamStudents.clear();
            }
        }
    }
    
    // Expose adviser IDs without exposing full adviser objects
    @Transient
    @JsonProperty("adviserIds")
    public List<Long> getAdviserIds() {
        return advisers != null ? advisers.stream()
                .map(User::getId)
                .collect(Collectors.toList()) : new ArrayList<>();
    }
    
    // Allow setting advisers by IDs during deserialization
    @Transient
    @JsonIgnore
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
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private SchoolClass schoolClass;
    
    @OneToMany(mappedBy = "team", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<TeamStudent> teamStudents = new ArrayList<>();
    
    // Convenience method to get students from teamStudents
    @Transient
    @JsonIgnore
    public List<Student> getMembers() {
        return teamStudents != null ? teamStudents.stream()
                .map(TeamStudent::getStudent)
                .collect(Collectors.toList()) : new ArrayList<>();
    }
    
    // Convenience method to set students as members
    @Transient
    @JsonIgnore
    public void setMembers(List<Student> members) {
        if (members != null && !members.isEmpty()) {
            if (teamStudents == null) {
                teamStudents = new ArrayList<>();
            }
            teamStudents.clear();
            for (Student student : members) {
                TeamStudent ts = new TeamStudent();
                ts.setStudent(student);
                ts.setTeam(this);
                teamStudents.add(ts);
            }
        } else {
            if (teamStudents != null) {
                teamStudents.clear();
            }
        }
    }
    
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