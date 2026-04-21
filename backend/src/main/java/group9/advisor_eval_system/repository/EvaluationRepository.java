package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.Evaluation;
import group9.advisor_eval_system.entity.Team;
import group9.advisor_eval_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByAdviser(User adviser);
    List<Evaluation> findByAdviserId(Long adviserId);
    List<Evaluation> findByTeam(Team team);
    List<Evaluation> findByTeamId(Long teamId);
    Optional<Evaluation> findByTeamIdAndAdviserId(Long teamId, Long adviserId);
    Optional<Evaluation> findByTeamIdAndAdviserIdAndQuestionnaireId(Long teamId, Long adviserId, Long questionnaireId);
    List<Evaluation> findByStatus(Evaluation.EvaluationStatus status);
    List<Evaluation> findByQuestionnaireId(Long questionnaireId);
}