package group9.advisor_eval_system.apeer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** DTO for submitting one peer evaluation. SRS 5.2 / SDD EvaluationSubmissionDTO. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationSubmissionRequest {
    @NotNull
    private Long evaluatorId;   // User id (student who submits)
    @NotNull
    private Long targetStudentId;
    @NotNull
    private Long activityId;
    /** Criterion name -> score (0-10). */
    @NotNull
    private Map<String, Integer> rubricScores;
    /** Mandatory comment. */
    private String commentContent;
}
