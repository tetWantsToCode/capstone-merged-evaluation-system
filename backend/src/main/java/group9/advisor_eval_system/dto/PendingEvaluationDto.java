package group9.advisor_eval_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingEvaluationDto {
    private Long evaluationId;
    private Long adviserId;
    private String adviserName;
    private Long classId;
    private String className;
    private Long teamId;
    private String teamName;
    private Long questionnaireId;
    private String questionnaireName;
}
