package group9.advisor_eval_system.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import group9.advisor_eval_system.entity.QuestionnaireItem;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateQuestionnaireRequest {
    
    @NotBlank(message = "Title is required")
    private String title;
    
    private String description;
    
    private List<QuestionnaireItemDto> questions;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionnaireItemDto {
        @NotBlank(message = "Question text is required")
        private String questionText;
        
        private Integer orderIndex;
        
        @NotBlank(message = "Question type is required")
        private String questionType; // NUMERIC_SCALE, TEXT, MULTIPLE_CHOICE, RATING
        
        private Integer maxScore;
        private Integer minScore;
        private List<String> choices; // Array of choices for multiple choice questions
        
        public QuestionnaireItem toEntity() {
            QuestionnaireItem item = new QuestionnaireItem();
            item.setQuestionText(this.questionText);
            item.setOrderIndex(this.orderIndex != null ? this.orderIndex : 0);
            item.setQuestionType(QuestionnaireItem.QuestionType.valueOf(this.questionType));
            item.setMaxScore(this.maxScore);
            item.setMinScore(this.minScore);
            
            // Convert choices array to JSON string
            if (this.choices != null && !this.choices.isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    item.setChoices(mapper.writeValueAsString(this.choices));
                } catch (Exception e) {
                    item.setChoices(null);
                }
            }
            
            return item;
        }
    }
}
