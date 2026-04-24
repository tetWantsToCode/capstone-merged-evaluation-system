package group9.advisor_eval_system.dto;

import group9.advisor_eval_system.entity.StudentEvaluation;
import group9.advisor_eval_system.entity.Student;
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
public class StudentEvaluationResponse {

    private Long id;
    private String teamName;
    private Long evaluatorId;
    private String evaluatorName;
    private Long evaluateeId;
    private String evaluateeName;
    private Boolean isSelf;
    private QuestionnaireWithItemsDto questionnaire;
    private List<StudentEvaluationScoreDto> scores;
    private String status;
    private Double averageScore;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StudentEvaluationResponse fromEntity(StudentEvaluation evaluation) {
        if (evaluation == null) {
            return null;
        }

        StudentEvaluationResponse dto = new StudentEvaluationResponse();
        dto.setId(evaluation.getId());

        // Team info
        try {
            if (evaluation.getStudent().getTeamStudents() != null && !evaluation.getStudent().getTeamStudents().isEmpty()) {
                dto.setTeamName(evaluation.getStudent().getTeamStudents().get(0).getTeam().getName());
            }
        } catch (Exception e) {}

        // Evaluator (Student)
        Student evaluator = evaluation.getStudent();
        dto.setEvaluatorId(evaluator.getId());
        dto.setEvaluatorName((evaluator.getFirstName() + " " + evaluator.getLastName()).trim());

        // Evaluatee (Peer or Self)
        if (evaluation.getEvaluatee() != null) {
            Student evaluatee = evaluation.getEvaluatee();
            dto.setEvaluateeId(evaluatee.getId());
            dto.setEvaluateeName((evaluatee.getFirstName() + " " + evaluatee.getLastName()).trim());
            dto.setIsSelf(evaluatee.getId().equals(evaluator.getId()));
        } else {
            dto.setEvaluateeName("Self");
            dto.setIsSelf(true);
        }

        // Questionnaire
        if (evaluation.getQuestionnaire() != null) {
            dto.setQuestionnaire(QuestionnaireWithItemsDto.fromEntity(evaluation.getQuestionnaire()));
        }

        // Scores
        if (evaluation.getScores() != null) {
            dto.setScores(evaluation.getScores().stream()
                    .map(StudentEvaluationScoreDto::fromEntity)
                    .collect(Collectors.toList()));
            
            // Calculate Average
            List<Double> numericValues = evaluation.getScores().stream()
                    .map(s -> s.getNumericScore())
                    .filter(v -> v != null)
                    .collect(Collectors.toList());
            
            if (!numericValues.isEmpty()) {
                double sum = numericValues.stream().mapToDouble(Double::doubleValue).sum();
                dto.setAverageScore(sum / numericValues.size());
            }
        } else {
            dto.setScores(new ArrayList<>());
        }

        dto.setStatus(evaluation.getStatus().name());
        dto.setSubmittedAt(evaluation.getSubmittedAt());
        dto.setCreatedAt(evaluation.getCreatedAt());
        dto.setUpdatedAt(evaluation.getUpdatedAt());

        return dto;
    }
}
