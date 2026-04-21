package group9.advisor_eval_system.apeer.repository;

import group9.advisor_eval_system.apeer.entity.EvaluationActivity;
import group9.advisor_eval_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationActivityRepository extends JpaRepository<EvaluationActivity, Long> {
    List<EvaluationActivity> findByCreatedByTeacherIdOrderByCreatedAtDesc(Long teacherId);
    List<EvaluationActivity> findByCreatedByTeacherAndIsActiveTrueOrderByCreatedAtDesc(User teacher);
    List<EvaluationActivity> findBySchoolClassIdInAndIsActiveTrueOrderByCreatedAtDesc(List<Long> classIds);
}
