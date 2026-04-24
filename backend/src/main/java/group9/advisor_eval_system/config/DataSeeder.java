package group9.advisor_eval_system.config;

import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    private static final String TEACHER_ADMIN_EMAIL = "draysanity@gmail.com";

    @Override
    public void run(String... args) throws Exception {
        seedTeacherAdminAccount();
        seedStudentUsers();
    }

    private void seedStudentUsers() {
        seedStudentUser("Adriane", "Queddeng", "adrianequeddeng@gmail.com");
    }

    private void seedStudentUser(String firstName, String lastName, String email) {
        if (!userRepository.existsByEmail(email)) {
            User student = new User();
            student.setFirstName(firstName);
            student.setLastName(lastName);
            student.setEmail(email);
            student.setRole(User.UserRole.STUDENT);
            student.setIsActive(true);
            student.setIsGoogleLinked(false);
            userRepository.save(student);
            System.out.println("=== Seeded student user: " + firstName + " " + lastName + " (" + email + ") ===");
        }
    }

    private void seedTeacherAdminAccount() {
        if (!userRepository.existsByEmail(TEACHER_ADMIN_EMAIL)) {
            User teacherAdmin = new User();
            teacherAdmin.setFirstName("System");
            teacherAdmin.setLastName("Teacher");
            teacherAdmin.setEmail(TEACHER_ADMIN_EMAIL);
            teacherAdmin.setRole(User.UserRole.TEACHER);
            teacherAdmin.setIsActive(true);
            teacherAdmin.setIsGoogleLinked(false);
            userRepository.save(teacherAdmin);
            System.out.println(
                    "=== Default teacher admin account created: " + TEACHER_ADMIN_EMAIL + " (OAuth login only) ===");
        }
    }
}
