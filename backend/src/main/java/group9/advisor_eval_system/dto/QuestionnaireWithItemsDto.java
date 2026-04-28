package group9.advisor_eval_system.dto;

import group9.advisor_eval_system.entity.Questionnaire;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireWithItemsDto {
    
    private Long id;
    private String title;
    private String description;
    private String googleFormUrl;
    private List<QuestionnaireItemDto> items; // Questions not in sections (legacy)
    private List<QuestionnaireSectionDto> sections; // New: sections with their questions
    
    public static QuestionnaireWithItemsDto fromEntity(Questionnaire questionnaire) {
        if (questionnaire == null) {
            return null;
        }
        
        QuestionnaireWithItemsDto dto = new QuestionnaireWithItemsDto();
        dto.setId(questionnaire.getId());
        dto.setTitle(questionnaire.getTitle());
        dto.setDescription(questionnaire.getDescription());
        dto.setGoogleFormUrl(questionnaire.getGoogleFormUrl());
        
        // Convert sections if present
        if (questionnaire.getSections() != null && !questionnaire.getSections().isEmpty()) {
            try {
                dto.setSections(
                    questionnaire.getSections().stream()
                        .filter(section -> section != null)
                        .map(QuestionnaireSectionDto::fromEntity)
                        .sorted((a, b) -> Integer.compare(
                            a.getOrderIndex() != null ? a.getOrderIndex() : 0,
                            b.getOrderIndex() != null ? b.getOrderIndex() : 0
                        ))
                        .collect(Collectors.toList())
                );
            } catch (Exception e) {
                dto.setSections(new ArrayList<>());
            }
        } else {
            dto.setSections(new ArrayList<>());
        }
        
        // Convert items to DTOs with null safety (items not in sections)
        try {
            if (questionnaire.getItems() != null && !questionnaire.getItems().isEmpty()) {
                dto.setItems(
                    questionnaire.getItems().stream()
                        .filter(item -> item != null && item.getSection() == null) // Only items not in sections
                        .map(QuestionnaireItemDto::fromEntity)
                        .sorted((a, b) -> Integer.compare(
                            a.getOrderIndex() != null ? a.getOrderIndex() : 0,
                            b.getOrderIndex() != null ? b.getOrderIndex() : 0
                        ))
                        .collect(Collectors.toList())
                );
            } else {
                dto.setItems(new ArrayList<>());
            }
        } catch (Exception e) {
            // If items can't be loaded, return empty list
            dto.setItems(new ArrayList<>());
        }
        
        return dto;
    }
}
