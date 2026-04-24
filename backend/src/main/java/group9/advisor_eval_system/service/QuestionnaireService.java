package group9.advisor_eval_system.service;

import com.google.api.services.forms.v1.model.Form;
import group9.advisor_eval_system.dto.CreateQuestionnaireRequest;
import group9.advisor_eval_system.entity.*;
import group9.advisor_eval_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
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
    private final QuestionnaireSectionRepository questionnaireSectionRepository;
    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final GoogleFormsService googleFormsService;

    /**
     * Create a new questionnaire with Google Form
     */
    @Transactional
    public Questionnaire createQuestionnaire(Long teacherId, String title, String description,
                                           List<QuestionnaireItem> questions,
                                           List<CreateQuestionnaireRequest.QuestionnaireSectionInputDto> sections,
                                           String targetRole,
                                           LocalDateTime deadlineAt) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        if (teacher.getRole() != User.UserRole.TEACHER) {
            throw new RuntimeException("Only teachers can create questionnaires");
        }

        if (!teacher.getIsGoogleLinked()) {
            throw new RuntimeException("Teacher must link Google account first");
        }

        Form googleForm;
        int totalQuestionCount = 0;

        // Create Google Form with appropriate method based on whether sections are used
        if (sections != null && !sections.isEmpty()) {
            // Create list of QuestionnaireSection entities for Google Forms API
            List<QuestionnaireSection> sectionEntities = new ArrayList<>();
            for (CreateQuestionnaireRequest.QuestionnaireSectionInputDto sectionDto : sections) {
                QuestionnaireSection section = new QuestionnaireSection();
                section.setSectionTitle(sectionDto.getSectionTitle());
                section.setSectionDescription(sectionDto.getSectionDescription());
                section.setOrderIndex(sectionDto.getOrderIndex() != null ? sectionDto.getOrderIndex() : 0);

                // Convert items to entities
                List<QuestionnaireItem> sectionItems = new ArrayList<>();
                if (sectionDto.getItems() != null) {
                    for (int i = 0; i < sectionDto.getItems().size(); i++) {
                        QuestionnaireItem item = sectionDto.getItems().get(i).toEntity();
                        item.setOrderIndex(i);
                        sectionItems.add(item);
                        totalQuestionCount++;
                    }
                }
                section.setItems(new java.util.HashSet<>(sectionItems));
                sectionEntities.add(section);
            }

            // Create Google Form with sections and page breaks
            googleForm = googleFormsService.createGoogleFormWithSections(
                    teacherId, title, description, questions != null ? questions : List.of(), sectionEntities);
        } else {
            // Create Google Form without sections (legacy)
            List<QuestionnaireItem> allQuestions = new ArrayList<>(questions != null ? questions : List.of());
            totalQuestionCount = allQuestions.size();
            googleForm = googleFormsService.createGoogleForm(teacherId, title, description, allQuestions);
        }

        // Also count loose questions if they exist
        if (questions != null && !questions.isEmpty()) {
            totalQuestionCount += questions.size();
        }

        // Create questionnaire entity
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setTitle(title);
        questionnaire.setDescription(description);
        questionnaire.setGoogleFormId(googleForm.getFormId());
        questionnaire.setGoogleFormUrl(googleForm.getResponderUri());
        questionnaire.setCreatedByTeacher(teacher);
        questionnaire.setIsActive(true);
        questionnaire.setDeadlineAt(normalizeAndValidateDeadline(deadlineAt));

        // Set target audience
        if (targetRole != null) {
            try {
                questionnaire.setTarget(Questionnaire.QuestionnaireTarget.valueOf(targetRole.toUpperCase()));
            } catch (IllegalArgumentException e) {
                questionnaire.setTarget(Questionnaire.QuestionnaireTarget.ADVISER);
            }
        } else {
            questionnaire.setTarget(Questionnaire.QuestionnaireTarget.ADVISER);
        }

        Questionnaire savedQuestionnaire = questionnaireRepository.save(questionnaire);

        // Save sections and their questions
        if (sections != null && !sections.isEmpty()) {
            for (CreateQuestionnaireRequest.QuestionnaireSectionInputDto sectionDto : sections) {
                QuestionnaireSection section = new QuestionnaireSection();
                section.setSectionTitle(sectionDto.getSectionTitle());
                section.setSectionDescription(sectionDto.getSectionDescription());
                section.setOrderIndex(sectionDto.getOrderIndex() != null ? sectionDto.getOrderIndex() : 0);
                section.setQuestionnaire(savedQuestionnaire);

                QuestionnaireSection savedSection = questionnaireSectionRepository.save(section);

                // Save questions in this section
                if (sectionDto.getItems() != null && !sectionDto.getItems().isEmpty()) {
                    for (int i = 0; i < sectionDto.getItems().size(); i++) {
                        QuestionnaireItem item = sectionDto.getItems().get(i).toEntity();
                        item.setQuestionnaire(savedQuestionnaire);
                        item.setSection(savedSection);
                        item.setOrderIndex(i);
                        questionnaireItemRepository.save(item);
                    }
                }
            }
        }

        // Save loose questions (not in sections)
        if (questions != null && !questions.isEmpty()) {
            for (int i = 0; i < questions.size(); i++) {
                QuestionnaireItem item = questions.get(i);
                item.setQuestionnaire(savedQuestionnaire);
                item.setOrderIndex(i);
                questionnaireItemRepository.save(item);
            }
        }

        log.info("Created questionnaire {} with {} questions and {} sections",
                savedQuestionnaire.getId(),
                totalQuestionCount,
                sections != null ? sections.size() : 0);

        return savedQuestionnaire;
    }

    /**
     * Create a new questionnaire with Google Form (legacy method for backward
     * compatibility)
     */
    @Transactional
    public Questionnaire createQuestionnaire(Long teacherId, String title, String description,
            List<QuestionnaireItem> questions) {
        return createQuestionnaire(teacherId, title, description, questions, List.of(), "ADVISER", null);
    }

    private LocalDateTime normalizeAndValidateDeadline(LocalDateTime deadlineAt) {
        if (deadlineAt == null) {
            return null;
        }
        if (!deadlineAt.isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Deadline must be in the future");
        }
        return deadlineAt.withSecond(0).withNano(0);
    }

    @Transactional
    public int closeExpiredQuestionnaires() {
        List<Questionnaire> expired = questionnaireRepository.findByIsActiveTrueAndDeadlineAtBefore(LocalDateTime.now());
        if (expired.isEmpty()) {
            return 0;
        }

        LocalDateTime closedAt = LocalDateTime.now();
        for (Questionnaire questionnaire : expired) {
            questionnaire.setIsActive(false);
            questionnaire.setIsLocked(true);
            if (questionnaire.getLockedAt() == null) {
                questionnaire.setLockedAt(closedAt);
            }
        }
        questionnaireRepository.saveAll(expired);
        log.info("Auto-closed {} expired questionnaire(s)", expired.size());
        return expired.size();
    }

    @Scheduled(fixedDelay = 60000)
    public void closeExpiredQuestionnairesOnSchedule() {
        try {
            closeExpiredQuestionnaires();
        } catch (Exception e) {
            log.error("Failed to auto-close expired questionnaires", e);
        }
    }

    @Transactional
    public void ensureQuestionnaireOpenForResponses(Long questionnaireId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));
        ensureQuestionnaireOpenForResponses(questionnaire);
    }

    @Transactional
    public void ensureQuestionnaireOpenForResponses(Questionnaire questionnaire) {
        LocalDateTime deadlineAt = questionnaire.getDeadlineAt();
        if (deadlineAt != null && !deadlineAt.isAfter(LocalDateTime.now())) {
            if (Boolean.TRUE.equals(questionnaire.getIsActive())) {
                questionnaire.setIsActive(false);
                questionnaire.setIsLocked(true);
                if (questionnaire.getLockedAt() == null) {
                    questionnaire.setLockedAt(LocalDateTime.now());
                }
                questionnaireRepository.save(questionnaire);
            }
            throw new RuntimeException("This questionnaire is closed because the deadline has passed");
        }

        if (!Boolean.TRUE.equals(questionnaire.getIsActive())) {
            throw new RuntimeException("This questionnaire is not active");
        }
    }

    /**
     * Get all questionnaires created by a teacher
     */
    public List<Questionnaire> getQuestionnairesByTeacher(Long teacherId) {
        closeExpiredQuestionnaires();
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        if (teacher.getRole() != User.UserRole.TEACHER) {
            throw new RuntimeException("Only teachers can access questionnaires");
        }

        return questionnaireRepository.findByCreatedByTeacherId(teacherId);
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
        closeExpiredQuestionnaires();
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

        // Get questionnaires assigned to those classes with target ADVISER
        return questionnaireRepository.findByAssignedClassesIdInAndIsActiveTrueAndTarget(new ArrayList<>(classIds), Questionnaire.QuestionnaireTarget.ADVISER);
    }

    /**
     * Get questionnaires for a specific class
     */
    public List<Questionnaire> getQuestionnairesByClass(Long classId) {
        closeExpiredQuestionnaires();
        SchoolClass schoolClass = schoolClassRepository.findById(classId).orElse(null);
        List<Questionnaire> questionnaires = new ArrayList<>();
        if (schoolClass != null) {
            questionnaires.addAll(questionnaireRepository.findByAssignedClassesContainingAndIsActiveTrueAndTarget(schoolClass, Questionnaire.QuestionnaireTarget.ADVISER));
        }
        return questionnaires;
    }

    /**
     * Get questionnaires for a specific class for teachers (includes inactive)
     */
    public List<Questionnaire> getQuestionnairesByClassForTeacher(Long classId, Long teacherId) {
        closeExpiredQuestionnaires();
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!schoolClass.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("You can only view questionnaires for your own classes");
        }

        return questionnaireRepository.findByAssignedClassesContaining(schoolClass);
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

        // Check if questionnaire is locked (has responses)
        if (questionnaire.getIsLocked() != null && questionnaire.getIsLocked()) {
            throw new RuntimeException("Cannot edit locked questionnaire. It has been answered by advisers.");
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

    /**
     * Update questionnaire items (questions with correct answers and points)
     */
    @Transactional
    public QuestionnaireItem updateQuestionnaireItem(Long questionnaireId, Long itemId,
            String questionText, String correctAnswer, Integer pointsValue, Long teacherId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        // Verify teacher owns this questionnaire
        if (!questionnaire.getCreatedByTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("You can only update your own questionnaires");
        }

        // Check if questionnaire is locked
        if (questionnaire.getIsLocked() != null && questionnaire.getIsLocked()) {
            throw new RuntimeException("Cannot edit locked questionnaire. It has been answered by advisers.");
        }

        QuestionnaireItem item = questionnaireItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        if (!item.getQuestionnaire().getId().equals(questionnaireId)) {
            throw new RuntimeException("Question does not belong to this questionnaire");
        }

        if (questionText != null && !questionText.isEmpty()) {
            item.setQuestionText(questionText);
        }
        if (correctAnswer != null) {
            item.setCorrectAnswer(correctAnswer);
        }
        if (pointsValue != null && pointsValue > 0) {
            item.setPointsValue(pointsValue);
        }

        QuestionnaireItem saved = questionnaireItemRepository.save(item);
        log.info("Updated questionnaire item {} with correct answer and points", itemId);

        return saved;
    }

    /**
     * Check if questionnaire is locked
     */
    public Boolean isQuestionnaireLocked(Long questionnaireId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));
        return questionnaire.getIsLocked() != null && questionnaire.getIsLocked();
    }

    /**
     * Lock questionnaire (called when first evaluation is submitted)
     */
    @Transactional
    public void lockQuestionnaire(Long questionnaireId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        if (!questionnaire.getIsLocked()) {
            questionnaire.setIsLocked(true);
            questionnaire.setLockedAt(java.time.LocalDateTime.now());
            questionnaireRepository.save(questionnaire);
            log.info("Locked questionnaire {} at {}", questionnaireId, questionnaire.getLockedAt());
        }
    }

    /**
     * Activate/deactivate questionnaire without changing assignments
     */
    @Transactional
    public Questionnaire updateQuestionnaireStatus(Long questionnaireId, Boolean isActive, Long teacherId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        if (!questionnaire.getCreatedByTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("You can only update your own questionnaires");
        }

        if (isActive == null) {
            throw new RuntimeException("isActive is required");
        }

        questionnaire.setIsActive(isActive);

        // Re-activating should reopen questionnaire usage for advisers.
        if (Boolean.TRUE.equals(isActive)) {
            questionnaire.setIsLocked(false);
            questionnaire.setLockedAt(null);
        }

        Questionnaire saved = questionnaireRepository.save(questionnaire);

        log.info("Updated questionnaire {} status to {} (isLocked={})",
                questionnaireId,
                isActive,
                saved.getIsLocked());
        return saved;
    }
}
