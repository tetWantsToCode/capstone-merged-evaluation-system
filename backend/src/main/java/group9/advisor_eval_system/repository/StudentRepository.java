package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    // Find students by a specific class using the join table
    List<Student> findByClassesId(Long classId);
    Optional<Student> findByStudentId(String studentId);
    boolean existsByStudentId(String studentId);
}