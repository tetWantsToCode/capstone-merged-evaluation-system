package group9.advisor_eval_system.dto;

import group9.advisor_eval_system.entity.QuestionnaireSection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSectionDto {
    
    private Long id;
    private String sectionTitle;
    private String sectionDescription;
    private Integer orderIndex;
    private List<QuestionnaireItemDto> items;
    
    public static QuestionnaireSectionDto fromEntity(QuestionnaireSection section) {
        if (section == null) {
            return null;
        }
        
        QuestionnaireSectionDto dto = new QuestionnaireSectionDto();
        dto.setId(section.getId());
        dto.setSectionTitle(section.getSectionTitle());
        dto.setSectionDescription(section.getSectionDescription());
        dto.setOrderIndex(section.getOrderIndex());
        
        // Convert items to DTOs
        if (section.getItems() != null && !section.getItems().isEmpty()) {
            dto.setItems(
                section.getItems().stream()
                    .filter(item -> item != null)
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
        
        return dto;
    }
}
