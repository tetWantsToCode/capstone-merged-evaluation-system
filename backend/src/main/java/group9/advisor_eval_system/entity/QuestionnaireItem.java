package group9.advisor_eval_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questionnaire_id", nullable = false)
    private Questionnaire questionnaire;
    
    public enum QuestionType {
        NUMERIC_SCALE,
        TEXT,
        MULTIPLE_CHOICE,
        RATING
    }
}