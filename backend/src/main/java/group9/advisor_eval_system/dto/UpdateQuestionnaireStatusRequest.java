package group9.advisor_eval_system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuestionnaireStatusRequest {

    @NotNull(message = "isActive is required")
    private Boolean isActive;
}
