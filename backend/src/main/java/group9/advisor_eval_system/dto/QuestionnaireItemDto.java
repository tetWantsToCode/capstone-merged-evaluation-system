package group9.advisor_eval_system.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import group9.advisor_eval_system.entity.QuestionnaireItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireItemDto {
    
    private Long id;
    private String questionText;
    private Integer orderIndex;
    private String questionType;
    private Integer maxScore;
    private Integer minScore;
    private List<String> choices;
    private Boolean required;
    private String googleFormItemId;
    private String googleQuestionId;
    
    public static QuestionnaireItemDto fromEntity(QuestionnaireItem item) {
        if (item == null) {
            return null;
        }
        
        QuestionnaireItemDto dto = new QuestionnaireItemDto();
        dto.setId(item.getId());
        dto.setQuestionText(item.getQuestionText());
        dto.setOrderIndex(item.getOrderIndex() != null ? item.getOrderIndex() : 0);
        dto.setQuestionType(item.getQuestionType() != null ? item.getQuestionType().name() : "TEXT");
        dto.setMaxScore(item.getMaxScore());
        dto.setMinScore(item.getMinScore());
        dto.setRequired(item.getRequired() == null || item.getRequired());
        dto.setGoogleFormItemId(item.getGoogleFormItemId());
        dto.setGoogleQuestionId(item.getGoogleQuestionId());
        
        // Parse choices from JSON string to List with null safety
        if (item.getChoices() != null && !item.getChoices().trim().isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String[] choicesArray = mapper.readValue(item.getChoices(), String[].class);
                dto.setChoices(List.of(choicesArray));
            } catch (Exception e) {
                // If parsing fails, return empty list
                dto.setChoices(new ArrayList<>());
            }
        } else {
            dto.setChoices(new ArrayList<>());
        }
        
        return dto;
    }
}
