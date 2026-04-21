package group9.advisor_eval_system.apeer.repository;

import group9.advisor_eval_system.apeer.entity.EvaluationActivity;
import group9.advisor_eval_system.apeer.entity.PeerEvaluation;
import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PeerEvaluationRepository extends JpaRepository<PeerEvaluation, Long> {
    List<PeerEvaluation> findByActivityId(Long activityId);
    List<PeerEvaluation> findByActivityAndEvaluator(EvaluationActivity activity, User evaluator);
    List<PeerEvaluation> findByActivityAndTargetStudent(EvaluationActivity activity, Student targetStudent);
    Optional<PeerEvaluation> findByActivityIdAndEvaluatorIdAndTargetStudentId(Long activityId, Long evaluatorId, Long targetStudentId);
    List<PeerEvaluation> findByTargetStudentId(Long targetStudentId);
}
