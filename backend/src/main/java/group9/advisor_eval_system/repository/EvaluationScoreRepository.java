package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.EvaluationScore;
import group9.advisor_eval_system.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationScoreRepository extends JpaRepository<EvaluationScore, Long> {
    List<EvaluationScore> findByEvaluation(Evaluation evaluation);
    List<EvaluationScore> findByEvaluationId(Long evaluationId);
    void deleteByEvaluationId(Long evaluationId);
}