package group9.advisor_eval_system.dto;

import group9.advisor_eval_system.entity.Questionnaire;
import group9.advisor_eval_system.entity.SchoolClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import group9.advisor_eval_system.dto.QuestionnaireSectionDto;
import group9.advisor_eval_system.dto.QuestionnaireItemDto;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireResponse {

    private Long id;
    private String title;
    private String description;
    private String googleFormId;
    private String googleFormUrl;
    private Boolean isActive;
    private Boolean isLocked;
    private LocalDateTime lockedAt;
    private Long createdByTeacherId;
    private String createdByTeacherName;
    private List<Long> assignedClassIds;
    private List<String> assignedClassNames;
    private Integer questionCount;
    private String target;
    private LocalDateTime deadlineAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Additional fields for questions
    private List<QuestionnaireItemDto> items;
    private List<QuestionnaireSectionDto> sections;

    public static QuestionnaireResponse fromEntity(Questionnaire questionnaire) {
        QuestionnaireResponse response = new QuestionnaireResponse();
        response.setId(questionnaire.getId());
        response.setTitle(questionnaire.getTitle());
        response.setDescription(questionnaire.getDescription());
        response.setGoogleFormId(questionnaire.getGoogleFormId());
        response.setGoogleFormUrl(questionnaire.getGoogleFormUrl());
        response.setIsActive(questionnaire.getIsActive());
        response.setIsLocked(questionnaire.getIsLocked() != null && questionnaire.getIsLocked());
        response.setLockedAt(questionnaire.getLockedAt());
        response.setTarget(questionnaire.getTarget() != null ? questionnaire.getTarget().name() : null);
        response.setDeadlineAt(questionnaire.getDeadlineAt());

        if (questionnaire.getCreatedByTeacher() != null) {
            response.setCreatedByTeacherId(questionnaire.getCreatedByTeacher().getId());
            response.setCreatedByTeacherName(
                    questionnaire.getCreatedByTeacher().getFirstName() + " " +
                            questionnaire.getCreatedByTeacher().getLastName());
        }

        try {
            if (questionnaire.getAssignedClasses() != null) {
                // Create a defensive copy to avoid concurrent modification
                Set<SchoolClass> classesCopy = new HashSet<>(questionnaire.getAssignedClasses());
                response.setAssignedClassIds(
                        classesCopy.stream()
                                .map(SchoolClass::getId)
                                .collect(Collectors.toList()));
                response.setAssignedClassNames(
                        classesCopy.stream()
                                .map(SchoolClass::getName)
                                .collect(Collectors.toList()));
            }
        } catch (Exception e) {
            response.setAssignedClassIds(new ArrayList<>());
            response.setAssignedClassNames(new ArrayList<>());
        }

        // Note: questionCount is set by the controller using direct database query
        response.setQuestionCount(0);

        response.setCreatedAt(questionnaire.getCreatedAt());
        response.setUpdatedAt(questionnaire.getUpdatedAt());

        // Convert sections if present
        if (questionnaire.getSections() != null && !questionnaire.getSections().isEmpty()) {
            try {
                response.setSections(
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
                response.setSections(new ArrayList<>());
            }
        } else {
            response.setSections(new ArrayList<>());
        }

        // Convert items to DTOs (items not in sections)
        try {
            if (questionnaire.getItems() != null && !questionnaire.getItems().isEmpty()) {
                response.setItems(
                    questionnaire.getItems().stream()
                        .filter(item -> item != null && item.getSection() == null)
                        .map(QuestionnaireItemDto::fromEntity)
                        .sorted((a, b) -> Integer.compare(
                            a.getOrderIndex() != null ? a.getOrderIndex() : 0,
                            b.getOrderIndex() != null ? b.getOrderIndex() : 0
                        ))
                        .collect(Collectors.toList())
                );
            } else {
                response.setItems(new ArrayList<>());
            }
        } catch (Exception e) {
            response.setItems(new ArrayList<>());
        }

        return response;
    }
}
