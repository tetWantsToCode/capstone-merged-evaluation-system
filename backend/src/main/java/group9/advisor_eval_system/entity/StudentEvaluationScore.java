package group9.advisor_eval_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_evaluation_scores")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentEvaluationScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private Double numericScore;

    @Column(length = 2000)
    private String textResponse;

    @Column(nullable = true)
    private Boolean isCorrect;

    @Column(nullable = true)
    private Integer pointsAwarded;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_evaluation_id", nullable = false)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private StudentEvaluation studentEvaluation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "questionnaire_item_id", nullable = true)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private QuestionnaireItem questionnaireItem;
}
