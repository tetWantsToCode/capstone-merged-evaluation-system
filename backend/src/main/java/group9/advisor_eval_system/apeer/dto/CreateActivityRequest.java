package group9.advisor_eval_system.apeer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** DTO for creating a peer evaluation activity. SRS 5.1 / SDD ActivityDTO. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateActivityRequest {
    @NotBlank(message = "Title is required")
    private String title;

    /** Rubric criterion names, e.g. ["Participation", "Cooperation", "Leadership"]. */
    private List<String> rubricCriteria;

    private LocalDateTime deadline;

    /** Optional: class ID so activity is for students in this class. */
    private Long classId;
}
