package group9.advisor_eval_system.dto;

import group9.advisor_eval_system.entity.EvaluationScore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationScoreDto {
    
    private Long id;
    private Long questionnaireItemId;
    private String questionText;
    private String questionType;
    private Double numericScore;
    private String textResponse;
    
    public static EvaluationScoreDto fromEntity(EvaluationScore score) {
        if (score == null) {
            return null;
        }
        
        EvaluationScoreDto dto = new EvaluationScoreDto();
        dto.setId(score.getId());
        
        if (score.getQuestionnaireItem() != null) {
            dto.setQuestionnaireItemId(score.getQuestionnaireItem().getId());
            dto.setQuestionText(score.getQuestionnaireItem().getQuestionText());
            dto.setQuestionType(score.getQuestionnaireItem().getQuestionType() != null 
                ? score.getQuestionnaireItem().getQuestionType().name() 
                : "TEXT");
        }
        
        dto.setNumericScore(score.getNumericScore());
        dto.setTextResponse(score.getTextResponse());
        return dto;
    }
}
