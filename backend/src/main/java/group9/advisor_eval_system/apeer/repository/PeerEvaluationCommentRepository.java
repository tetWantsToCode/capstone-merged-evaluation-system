package group9.advisor_eval_system.apeer.repository;

import group9.advisor_eval_system.apeer.entity.PeerEvaluation;
import group9.advisor_eval_system.apeer.entity.PeerEvaluationComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PeerEvaluationCommentRepository extends JpaRepository<PeerEvaluationComment, Long> {
    Optional<PeerEvaluationComment> findByEvaluation(PeerEvaluation evaluation);
    Optional<PeerEvaluationComment> findByEvaluationId(Long evaluationId);
}
