package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
    List<SchoolClass> findByTeacher(User teacher);

    @Query("SELECT s FROM SchoolClass s WHERE s.teacher.id = :teacherId")
    List<SchoolClass> findByTeacherId(@Param("teacherId") Long teacherId);

    List<SchoolClass> findByIsActiveTrue();
}