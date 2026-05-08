package group9.advisor_eval_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "questionnaire_sections")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSection {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 500)
    private String sectionTitle;
    
    @Column(length = 1000)
    private String sectionDescription;
    
    @Column(nullable = false)
    private Integer orderIndex; // For sorting sections
    
    @Column(nullable = false)
    private Boolean evaluateIndividuals = false; // True = evaluate each student individually in this section
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questionnaire_id", nullable = false)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Questionnaire questionnaire;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Set<QuestionnaireItem> items = new HashSet<>();
}
