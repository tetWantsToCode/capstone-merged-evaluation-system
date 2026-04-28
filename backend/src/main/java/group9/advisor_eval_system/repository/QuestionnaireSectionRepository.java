package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.QuestionnaireSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionnaireSectionRepository extends JpaRepository<QuestionnaireSection, Long> {
    List<QuestionnaireSection> findByQuestionnaireIdOrderByOrderIndex(Long questionnaireId);
}
