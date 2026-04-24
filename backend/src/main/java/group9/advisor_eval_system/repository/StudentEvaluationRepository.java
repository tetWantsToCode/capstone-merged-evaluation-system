package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.StudentEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentEvaluationRepository extends JpaRepository<StudentEvaluation, Long> {
    
    Optional<StudentEvaluation> findByStudentIdAndQuestionnaireIdAndEvaluateeId(Long studentId, Long questionnaireId, Long evaluateeId);
    
    Optional<StudentEvaluation> findByStudentIdAndQuestionnaireIdAndEvaluateeIsNull(Long studentId, Long questionnaireId);
    
    @Query("SELECT e FROM StudentEvaluation e LEFT JOIN FETCH e.scores WHERE e.student.id = :studentId")
    List<StudentEvaluation> findByStudentIdWithScores(@Param("studentId") Long studentId);
    
    List<StudentEvaluation> findByQuestionnaireId(Long questionnaireId);

    List<StudentEvaluation> findByQuestionnaireIdAndEvaluateeId(Long questionnaireId, Long evaluateeId);

    List<StudentEvaluation> findByEvaluateeIdAndStatus(Long evaluateeId, StudentEvaluation.EvaluationStatus status);

    @Query("""
            SELECT DISTINCT e FROM StudentEvaluation e
            LEFT JOIN FETCH e.scores s
            LEFT JOIN FETCH s.questionnaireItem qi
            LEFT JOIN FETCH e.questionnaire q
            LEFT JOIN FETCH q.items qi2
            LEFT JOIN FETCH q.sections qs
            LEFT JOIN FETCH qs.items qsi
            LEFT JOIN FETCH e.student st
            LEFT JOIN FETCH st.teamStudents ts
            LEFT JOIN FETCH ts.team t
            LEFT JOIN FETCH e.evaluatee ev
            WHERE e.id = :id
            """)
    Optional<StudentEvaluation> findByIdWithDetails(@Param("id") Long id);
}
