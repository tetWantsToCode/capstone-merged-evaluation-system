package group9.advisor_eval_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
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
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false)
    private String firstName;
    
    @NotBlank
    @Column(nullable = false)
    private String lastName;
    
    @Email
    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;
    
    @NotBlank
    @Column(nullable = false)
    @JsonIgnore
    private String password; // Encrypted
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role; // TEACHER or ADVISER
    
    @Column(nullable = true)
    private String phoneNumber;
    
    @Column(nullable = true)
    private String department;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    // Google OAuth fields for account linking
    @Column(nullable = true)
    private String googleId;
    
    @Column(nullable = true, length = 1000)
    @JsonIgnore
    private String googleAccessToken;
    
    @Column(nullable = true, length = 1000)
    @JsonIgnore
    private String googleRefreshToken;
    
    @Column(nullable = true)
    private LocalDateTime googleTokenExpiry;
    
    @Column(nullable = true)
    private String googleEmail;
    
    @Column(nullable = false)
    private Boolean isGoogleLinked = false;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // Relationships
    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<SchoolClass> createdClasses = new ArrayList<>();
    
    @ManyToMany(mappedBy = "advisers")
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private List<Team> advisedTeams = new ArrayList<>();
    
    public enum UserRole {
        TEACHER,
        ADVISER
    }
}