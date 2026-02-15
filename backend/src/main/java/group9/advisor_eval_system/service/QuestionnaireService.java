package group9.advisor_eval_system.service;

import com.google.api.services.forms.v1.model.Form;
import group9.advisor_eval_system.entity.*;
import group9.advisor_eval_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionnaireService {

    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireItemRepository questionnaireItemRepository;
    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final GoogleFormsService googleFormsService;

    /**
     * Create a new questionnaire with Google Form
     */
    @Transactional
    public Questionnaire createQuestionnaire(Long teacherId, String title, String description, List<QuestionnaireItem> questions) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        if (teacher.getRole() != User.UserRole.TEACHER) {
            throw new RuntimeException("Only teachers can create questionnaires");
        }

        if (!teacher.getIsGoogleLinked()) {
            throw new RuntimeException("Teacher must link Google account first");
        }

        // Create Google Form
        Form googleForm = googleFormsService.createGoogleForm(teacherId, title, description, questions);

        // Create questionnaire entity
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setTitle(title);
        questionnaire.setDescription(description);
        questionnaire.setGoogleFormId(googleForm.getFormId());
        questionnaire.setGoogleFormUrl(googleForm.getResponderUri());
        questionnaire.setCreatedByTeacher(teacher);
        questionnaire.setIsActive(true);

        Questionnaire savedQuestionnaire = questionnaireRepository.save(questionnaire);

        // Save questions
        if (questions != null && !questions.isEmpty()) {
            for (QuestionnaireItem item : questions) {
                item.setQuestionnaire(savedQuestionnaire);
                questionnaireItemRepository.save(item);
            }
        }

        log.info("Created questionnaire {} with {} questions", savedQuestionnaire.getId(), 
            questions != null ? questions.size() : 0);

        return savedQuestionnaire;
    }

    /**
     * Get all questionnaires created by a teacher
     */
    public List<Questionnaire> getQuestionnairesByTeacher(Long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        if (teacher.getRole() != User.UserRole.TEACHER) {
            throw new RuntimeException("Only teachers can access questionnaires");
        }

        return questionnaireRepository.findByCreatedByTeacherIdAndIsActiveTrue(teacherId);
    }

    /**
     * Get questionnaire by ID
     */
    public Questionnaire getQuestionnaireById(Long questionnaireId) {
        return questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));
    }

    /**
     * Assign questionnaire to classes
     */
    @Transactional
    public Questionnaire assignToClasses(Long questionnaireId, List<Long> classIds, Long teacherId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        // Verify teacher owns this questionnaire
        if (!questionnaire.getCreatedByTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("You can only assign your own questionnaires");
        }

        // Get classes
        List<SchoolClass> classes = schoolClassRepository.findAllById(classIds);

        // Verify teacher owns these classes
        for (SchoolClass schoolClass : classes) {
            if (!schoolClass.getTeacher().getId().equals(teacherId)) {
                throw new RuntimeException("You can only assign questionnaires to your own classes");
            }
        }

        // Assign classes
        questionnaire.getAssignedClasses().addAll(classes);
        Questionnaire saved = questionnaireRepository.save(questionnaire);

        log.info("Assigned questionnaire {} to {} classes", questionnaireId, classIds.size());

        return saved;
    }

    /**
     * Remove questionnaire from classes
     */
    @Transactional
    public Questionnaire removeFromClasses(Long questionnaireId, List<Long> classIds, Long teacherId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        // Verify teacher owns this questionnaire
        if (!questionnaire.getCreatedByTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("You can only modify your own questionnaires");
        }

        // Remove classes
        questionnaire.getAssignedClasses().removeIf(c -> classIds.contains(c.getId()));
        Questionnaire saved = questionnaireRepository.save(questionnaire);

        log.info("Removed questionnaire {} from {} classes", questionnaireId, classIds.size());

        return saved;
    }

    /**
     * Get questionnaires available to an adviser through their assigned teams
     */
    public List<Questionnaire> getQuestionnairesForAdviser(Long adviserId) {
        User adviser = userRepository.findById(adviserId)
                .orElseThrow(() -> new RuntimeException("Adviser not found"));

        if (adviser.getRole() != User.UserRole.ADVISER) {
            throw new RuntimeException("Only advisers can access this endpoint");
        }

        // Get all teams the adviser is assigned to
        List<Team> adviserTeams = adviser.getAdvisedTeams();
        
        if (adviserTeams == null || adviserTeams.isEmpty()) {
            return new ArrayList<>();
        }

        // Get all classes from those teams
        Set<Long> classIds = adviserTeams.stream()
                .map(team -> team.getSchoolClass().getId())
                .collect(Collectors.toSet());
        
        if (classIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Get questionnaires assigned to those classes
        return questionnaireRepository.findByAssignedClassesIdInAndIsActiveTrue(new ArrayList<>(classIds));
    }

    /**
     * Get questionnaires for a specific class
     */
    public List<Questionnaire> getQuestionnairesByClass(Long classId) {
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        return questionnaireRepository.findByAssignedClassesContainingAndIsActiveTrue(schoolClass);
    }

    /**
     * Soft delete questionnaire
     */
    @Transactional
    public void deleteQuestionnaire(Long questionnaireId, Long teacherId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        // Verify teacher owns this questionnaire
        if (!questionnaire.getCreatedByTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("You can only delete your own questionnaires");
        }

        questionnaire.setIsActive(false);
        questionnaireRepository.save(questionnaire);

        log.info("Soft deleted questionnaire {}", questionnaireId);
    }

    /**
     * Update questionnaire details (not the form itself)
     */
    @Transactional
    public Questionnaire updateQuestionnaire(Long questionnaireId, String title, String description, Long teacherId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        // Verify teacher owns this questionnaire
        if (!questionnaire.getCreatedByTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("You can only update your own questionnaires");
        }

        if (title != null && !title.isEmpty()) {
            questionnaire.setTitle(title);
        }
        if (description != null) {
            questionnaire.setDescription(description);
        }

        Questionnaire saved = questionnaireRepository.save(questionnaire);

        log.info("Updated questionnaire {}", questionnaireId);

        return saved;
    }
}
