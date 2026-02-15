package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
    List<SchoolClass> findByTeacher(User teacher);
    List<SchoolClass> findByTeacherId(Long teacherId);
    List<SchoolClass> findByIsActiveTrue();
}