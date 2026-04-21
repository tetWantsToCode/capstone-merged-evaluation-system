package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    // Find students by a specific class using the join table
    List<Student> findByClassesId(Long classId);
    // Find distinct students by teacher (via classes) - distinct prevents duplicates
    @Query("SELECT DISTINCT s FROM Student s JOIN s.classes c WHERE c.teacher.id = :teacherId")
    List<Student> findByClassesTeacherId(@Param("teacherId") Long teacherId);
    // Find students created by a specific teacher
    List<Student> findByCreatedBy(Long teacherId);
    // Check if student ID exists for a specific teacher
    boolean existsByStudentIdAndCreatedBy(String studentId, Long createdBy);
    Optional<Student> findByStudentId(String studentId);
    boolean existsByStudentId(String studentId);
}