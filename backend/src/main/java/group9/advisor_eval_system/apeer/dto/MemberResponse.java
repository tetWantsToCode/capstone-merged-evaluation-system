package group9.advisor_eval_system.apeer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One group member for peer evaluation (ordered list). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponse {
    private Long studentId;
    private String studentIdentifier;  // studentId (school ID)
    private String firstName;
    private String lastName;
    private String email;
    /** 1-based order in the list (Member 1, Member 2, ...). */
    private int orderIndex;
}
