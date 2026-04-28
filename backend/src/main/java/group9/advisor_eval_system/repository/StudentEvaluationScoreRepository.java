package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.StudentEvaluationScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentEvaluationScoreRepository extends JpaRepository<StudentEvaluationScore, Long> {
    List<StudentEvaluationScore> findByStudentEvaluationId(Long studentEvaluationId);
    void deleteByStudentEvaluationId(Long studentEvaluationId);
}
