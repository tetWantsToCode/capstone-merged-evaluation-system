package group9.advisor_eval_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Student {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false)
    private String studentId; // School ID number
    
    @NotBlank
    @Column(nullable = false)
    private String firstName;
    
    @NotBlank
    @Column(nullable = false)
    private String lastName;
    
    @Column(nullable = true)
    private String email;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by", nullable = true)
    private Long createdBy; // teacher user ID who created/imported this student
    
    // Expose class IDs without exposing full class objects
    @JsonProperty("classIds")
    public List<Long> getClassIds() {
        return classes != null ? classes.stream()
                .map(SchoolClass::getId)
                .collect(Collectors.toList()) : new ArrayList<>();
    }
    
    // Allow setting classes by IDs during deserialization
    @JsonProperty("classIds")
    public void setClassIds(List<Long> classIds) {
        if (classIds != null && !classIds.isEmpty()) {
            this.classes = classIds.stream()
                    .map(id -> {
                        SchoolClass tempClass = new SchoolClass();
                        tempClass.setId(id);
                        return tempClass;
                    })
                    .collect(Collectors.toList());
        } else {
            this.classes = new ArrayList<>();
        }
    }
    
    // Relationships
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "student_classes",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "class_id")
    )
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<SchoolClass> classes = new ArrayList<>();
    
    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<TeamStudent> teamStudents = new ArrayList<>();
    
    // Convenience method to get teams from teamStudents
    @Transient
    @JsonProperty("teamIds")
    public List<Long> getTeamIds() {
        return teamStudents != null ? teamStudents.stream()
                .map(ts -> ts.getTeam().getId())
                .collect(Collectors.toList()) : new ArrayList<>();
    }
    
    // Convenience method to get Team objects
    @Transient
    public List<Team> getTeams() {
        return teamStudents != null ? teamStudents.stream()
                .map(TeamStudent::getTeam)
                .collect(Collectors.toList()) : new ArrayList<>();
    }
    
    // Convenience method to set teams by Team objects
    @Transient
    public void setTeams(List<Team> teams) {
        if (teams != null && !teams.isEmpty()) {
            if (teamStudents == null) {
                teamStudents = new ArrayList<>();
            }
            teamStudents.clear();
            for (Team team : teams) {
                TeamStudent ts = new TeamStudent();
                ts.setStudent(this);
                ts.setTeam(team);
                teamStudents.add(ts);
            }
        } else {
            if (teamStudents != null) {
                teamStudents.clear();
            }
        }
    }
}