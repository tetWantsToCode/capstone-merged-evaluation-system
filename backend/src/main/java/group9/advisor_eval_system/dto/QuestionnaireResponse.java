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

        return response;
    }
}
