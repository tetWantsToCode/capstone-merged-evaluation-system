package group9.advisor_eval_system.config;

import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.StudentRepository;
import group9.advisor_eval_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    private static final String TEACHER_ADMIN_EMAIL = "authortet@gmail.com";
    private static final String[] TEACHER_EMAILS = {
        TEACHER_ADMIN_EMAIL,
        "kodoku173@gmail.com"
    };
    private static final String[] STUDENT_EMAILS = {
        "keithalmodiel666@gmail.com",
        "stevenjantabungar@gmail.com"
    };

    @Override
    public void run(String... args) throws Exception {
        seedTeacherAdminAccount();
        seedStudentAccounts();
        seedStudentRecords();
    }

    private void seedTeacherAdminAccount() {
        for (String email : TEACHER_EMAILS) {
            if (!userRepository.existsByEmail(email)) {
                User teacherAdmin = new User();
                teacherAdmin.setFirstName("System");
                teacherAdmin.setLastName("Teacher");
                teacherAdmin.setEmail(email);
                teacherAdmin.setRole(User.UserRole.TEACHER);
                teacherAdmin.setIsActive(true);
                teacherAdmin.setIsGoogleLinked(false);
                userRepository.save(teacherAdmin);
                System.out.println("=== Teacher account created: " + email + " ===");
            }
        }
    }

    private void seedStudentAccounts() {
        for (String email : STUDENT_EMAILS) {
            userRepository.findByEmail(email).ifPresentOrElse(
                existing -> {
                    if (existing.getRole() != User.UserRole.STUDENT) {
                        existing.setRole(User.UserRole.STUDENT);
                        userRepository.save(existing);
                        System.out.println("=== User " + email + " updated to STUDENT role ===");
                    }
                },
                () -> {
                    User student = new User();
                    student.setFirstName("Student");
                    student.setLastName("User");
                    student.setEmail(email);
                    student.setRole(User.UserRole.STUDENT);
                    student.setIsActive(true);
                    student.setIsGoogleLinked(false);
                    userRepository.save(student);
                    System.out.println("=== Student account created: " + email + " ===");
                }
            );
        }
    }

    private void seedStudentRecords() {
        String[][] records = {
            {"STU-001", "Keith",  "Almodiel", STUDENT_EMAILS[0]},
            {"STU-002", "Steven", "Jantabungar", STUDENT_EMAILS[1]}
        };
        for (String[] r : records) {
            String email = r[3].toLowerCase().trim();
            if (!studentRepository.findByEmail(email).isPresent()) {
                Student s = new Student();
                s.setStudentId(r[0]);
                s.setFirstName(r[1]);
                s.setLastName(r[2]);
                s.setEmail(email);
                studentRepository.save(s);
                System.out.println("=== Student record created: " + email + " ===");
            }
        }
    }
}
