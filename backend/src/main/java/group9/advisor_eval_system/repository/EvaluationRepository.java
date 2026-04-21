package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.Evaluation;
import group9.advisor_eval_system.entity.Team;
import group9.advisor_eval_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT DISTINCT e FROM Evaluation e " +
            "LEFT JOIN FETCH e.team t " +
            "LEFT JOIN FETCH t.schoolClass " +
            "LEFT JOIN FETCH e.questionnaire " +
            "WHERE e.adviser.id = :adviserId")
    List<Evaluation> findByAdviserIdWithDetails(@Param("adviserId") Long adviserId);

    @Query("SELECT DISTINCT e FROM Evaluation e " +
            "LEFT JOIN FETCH e.questionnaire q " +
            "LEFT JOIN FETCH q.items " +
            "LEFT JOIN FETCH e.scores " +
            "WHERE e.adviser.id = :adviserId AND e.team.id = :teamId")
    List<Evaluation> findByAdviserIdAndTeamIdWithProgress(
            @Param("adviserId") Long adviserId,
            @Param("teamId") Long teamId);

    @Query("SELECT DISTINCT e FROM Evaluation e " +
            "JOIN e.questionnaire q " +
            "JOIN e.team t " +
            "JOIN t.schoolClass sc " +
            "JOIN e.adviser a " +
            "WHERE sc.teacher.id = :teacherId " +
            "AND e.status = 'IN_PROGRESS'")
    List<Evaluation> findPendingEvaluationsByTeacherId(@Param("teacherId") Long teacherId);
}