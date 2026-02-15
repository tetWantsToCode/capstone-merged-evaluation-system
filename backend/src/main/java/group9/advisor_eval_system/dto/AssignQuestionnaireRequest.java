package group9.advisor_eval_system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignQuestionnaireRequest {
    
    @NotEmpty(message = "Class IDs are required")
    private List<Long> classIds;
}
