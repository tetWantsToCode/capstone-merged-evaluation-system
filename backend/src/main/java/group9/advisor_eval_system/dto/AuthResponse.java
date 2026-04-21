package group9.advisor_eval_system.dto;

import group9.advisor_eval_system.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private User.UserRole role;
    private Boolean isActive;
    private String token;
    private String message;
    
    public AuthResponse(User user, String token, String message) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.isActive = user.getIsActive();
        this.token = token;
        this.message = message;
    }
}
