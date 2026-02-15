package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.Questionnaire;
import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionnaireRepository extends JpaRepository<Questionnaire, Long> {
    
    Optional<Questionnaire> findByGoogleFormId(String googleFormId);
    
    List<Questionnaire> findByIsActiveTrue();
    
    List<Questionnaire> findByCreatedByTeacherIdAndIsActiveTrue(Long teacherId);
    
    @Query("SELECT q FROM Questionnaire q JOIN q.assignedClasses c WHERE c.id IN :classIds AND q.isActive = true")
    List<Questionnaire> findByAssignedClassesIdInAndIsActiveTrue(@Param("classIds") List<Long> classIds);
    
    @Query("SELECT q FROM Questionnaire q JOIN q.assignedClasses c WHERE c = :schoolClass AND q.isActive = true")
    List<Questionnaire> findByAssignedClassesContainingAndIsActiveTrue(@Param("schoolClass") SchoolClass schoolClass);
    
    @Query("SELECT DISTINCT q FROM Questionnaire q LEFT JOIN FETCH q.items WHERE q.id = :id")
    Optional<Questionnaire> findByIdWithItems(@Param("id") Long id);
}