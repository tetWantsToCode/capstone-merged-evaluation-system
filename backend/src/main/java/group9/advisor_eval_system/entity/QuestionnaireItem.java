package group9.advisor_eval_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "questionnaire_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false, length = 1000)
    private String questionText;
    
    @Column(nullable = false)
    private Integer orderIndex; // For sorting questions
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType;
    
    @Column(nullable = true)
    private Integer maxScore; // For numeric scoring
    
    @Column(nullable = true)
    private Integer minScore;
    
    @Column(columnDefinition = "TEXT")
    private String choices; // JSON string storing multiple choice options
    
    @Column(nullable = true, length = 1000)
    private String correctAnswer; // Correct answer for auto-grading
    
    @Column(nullable = true)
    private Integer pointsValue = 1; // Points for correct answer (default 1)
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questionnaire_id", nullable = false)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Questionnaire questionnaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = true)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private QuestionnaireSection section; // Optional: question can belong to a section
    
    public enum QuestionType {
        NUMERIC_SCALE,
        TEXT,
        MULTIPLE_CHOICE,
        RATING
    }
}