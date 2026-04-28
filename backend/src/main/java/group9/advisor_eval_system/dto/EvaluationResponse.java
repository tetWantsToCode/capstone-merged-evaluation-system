package group9.advisor_eval_system.dto;

import group9.advisor_eval_system.entity.Evaluation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResponse {

    private Long id;
    private Long teamId;
    private String teamName;
    private String className;
    private Long adviserId;
    private String adviserName;
    private QuestionnaireWithItemsDto questionnaire;
    private List<EvaluationScoreDto> scores;
    private String generalComments;
    private String status;
    private Boolean allowEdit;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EvaluationResponse fromEntity(Evaluation evaluation) {
        if (evaluation == null) {
            return null;
        }

        EvaluationResponse dto = new EvaluationResponse();
        dto.setId(evaluation.getId());

        // Team info with null safety
        try {
            if (evaluation.getTeam() != null) {
                dto.setTeamId(evaluation.getTeam().getId());
                dto.setTeamName(evaluation.getTeam().getName());
                if (evaluation.getTeam().getSchoolClass() != null) {
                    dto.setClassName(evaluation.getTeam().getSchoolClass().getName());
                }
            }
        } catch (Exception e) {
            // Team info not available
        }

        // Adviser info with null safety
        try {
            if (evaluation.getAdviser() != null) {
                dto.setAdviserId(evaluation.getAdviser().getId());
                String firstName = evaluation.getAdviser().getFirstName() != null
                        ? evaluation.getAdviser().getFirstName()
                        : "";
                String lastName = evaluation.getAdviser().getLastName() != null ? evaluation.getAdviser().getLastName()
                        : "";
                dto.setAdviserName((firstName + " " + lastName).trim());
            }
        } catch (Exception e) {
            // Adviser info not available
        }

        // Questionnaire with items with null safety
        try {
            if (evaluation.getQuestionnaire() != null) {
                dto.setQuestionnaire(QuestionnaireWithItemsDto.fromEntity(evaluation.getQuestionnaire()));
            }
        } catch (Exception e) {
            // Questionnaire not available, create empty one
            QuestionnaireWithItemsDto emptyQuestionnaire = new QuestionnaireWithItemsDto();
            emptyQuestionnaire.setItems(new ArrayList<>());
            dto.setQuestionnaire(emptyQuestionnaire);
        }

        // Scores with null safety
        try {
            if (evaluation.getScores() != null && !evaluation.getScores().isEmpty()) {
                dto.setScores(
                        evaluation.getScores().stream()
                                .filter(score -> score != null)
                                .map(EvaluationScoreDto::fromEntity)
                                .filter(scoreDto -> scoreDto != null)
                                .collect(Collectors.toList()));
            } else {
                dto.setScores(new ArrayList<>());
            }
        } catch (Exception e) {
            dto.setScores(new ArrayList<>());
        }

        dto.setGeneralComments(evaluation.getGeneralComments());
        dto.setStatus(evaluation.getStatus() != null ? evaluation.getStatus().name() : "IN_PROGRESS");
        dto.setAllowEdit(evaluation.getAllowEdit() != null ? evaluation.getAllowEdit() : true);
        dto.setSubmittedAt(evaluation.getSubmittedAt());
        dto.setCreatedAt(evaluation.getCreatedAt());
        dto.setUpdatedAt(evaluation.getUpdatedAt());

        return dto;
    }
}
