package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/hello")
    public String hello() {
        return "Adviser Evaluation System is running!  🚀";
    }

    @PostMapping("/create-user")
    public ResponseEntity<User> createTestUser() {
        User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(User.UserRole.TEACHER);
        user.setDepartment("Computer Science");
        user.setIsActive(true);
        
        User savedUser = userRepository.save(user);
        return ResponseEntity.ok(savedUser);
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }
}