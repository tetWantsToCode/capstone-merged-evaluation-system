package group9.advisor_eval_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EvaluationAnalyticsRequest {

    // Mode: "chat" for user questions, "evaluation_summary" for auto analysis
    @NotBlank(message = "mode is required")
    @Pattern(regexp = "chat|evaluation_summary", message = "mode must be 'chat' or 'evaluation_summary'")
    private String mode;

    // Questionnaire ID to analyze
    private Long questionnaireId;

    // Team ID to focus on (optional)
    private Long teamId;

    // For chat mode: the user's question
    @Size(max = 2000, message = "query must be at most 2000 characters")
    private String query;

    // Context type hint (e.g., "analytics", "feedback")
    @Size(max = 50, message = "contextType must be at most 50 characters")
    private String contextType;
}
