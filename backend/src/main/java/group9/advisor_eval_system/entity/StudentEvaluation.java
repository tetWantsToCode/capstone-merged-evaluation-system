package group9.advisor_eval_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "student_evaluations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluationStatus status = EvaluationStatus.IN_PROGRESS;

    @Column(nullable = true)
    private LocalDateTime submittedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Relationships

    // The student evaluator (used for peer-to-peer evals — null for adviser evals)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = true)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questionnaire_id", nullable = true)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Questionnaire questionnaire;

    // The student being evaluated
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluatee_id", nullable = true)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Student evaluatee;

    // The adviser evaluator (used for adviser-student evals — null for peer evals)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adviser_id", nullable = true)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private User adviser;

    // The team context (used for adviser-student evals — null for peer evals)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = true)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Team team;

    @OneToMany(mappedBy = "studentEvaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Set<StudentEvaluationScore> scores = new HashSet<>();

    public enum EvaluationStatus {
        IN_PROGRESS,
        SUBMITTED
    }
}