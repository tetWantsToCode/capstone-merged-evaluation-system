package group9.advisor_eval_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportStudentDTO {
    private String studentId;
    private String firstName;
    private String lastName;
    private String email;
}
