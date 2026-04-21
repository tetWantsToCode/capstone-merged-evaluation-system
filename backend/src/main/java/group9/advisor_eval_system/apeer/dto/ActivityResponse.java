package group9.advisor_eval_system.apeer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Response DTO for an evaluation activity. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityResponse {
    private Long id;
    private String title;
    private LocalDateTime deadline;
    private Boolean isActive;
    private List<String> rubricCriteria;
    private Long createdByTeacherId;
    private Long classId;
    private LocalDateTime createdAt;
}
