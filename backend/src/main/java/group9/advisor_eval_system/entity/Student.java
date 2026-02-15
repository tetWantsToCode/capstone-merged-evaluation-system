package group9.advisor_eval_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.constraints.Email;
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
    
    @Email
    @Column(nullable = true)
    private String email;
    
    @Column(nullable = true)
    private String phoneNumber;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
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
    
    // Expose team IDs without exposing full team objects
    @JsonProperty("teamIds")
    public List<Long> getTeamIds() {
        return teams != null ? teams.stream()
                .map(Team::getId)
                .collect(Collectors.toList()) : new ArrayList<>();
    }
    
    // Allow setting teams by IDs during deserialization
    @JsonProperty("teamIds")
    public void setTeamIds(List<Long> teamIds) {
        if (teamIds != null && !teamIds.isEmpty()) {
            this.teams = teamIds.stream()
                    .map(id -> {
                        Team tempTeam = new Team();
                        tempTeam.setId(id);
                        return tempTeam;
                    })
                    .collect(Collectors.toList());
        } else {
            this.teams = new ArrayList<>();
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
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "student_teams",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<Team> teams = new ArrayList<>();
}