package group9.advisor_eval_system.service;

import com.google.api.services.forms.v1.model.Form;
import com.google.api.services.forms.v1.model.Item;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final EvaluationRepository evaluationRepository;
    private final StudentEvaluationRepository studentEvaluationRepository;
    private final EvaluationScoreRepository evaluationScoreRepository;
    private final StudentEvaluationScoreRepository studentEvaluationScoreRepository;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private UserManagementService userManagementService;

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
                section.setEvaluateIndividuals(sectionDto.getEvaluateIndividuals() != null ? sectionDto.getEvaluateIndividuals() : false);

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

        // Auto-assign to all existing classes belonging to this teacher
        List<SchoolClass> teacherClasses = schoolClassRepository.findByTeacherId(teacherId);
        if (!teacherClasses.isEmpty()) {
            savedQuestionnaire.getAssignedClasses().addAll(teacherClasses);
            savedQuestionnaire = questionnaireRepository.save(savedQuestionnaire);
        }

        // Save sections and their questions
        if (sections != null && !sections.isEmpty()) {
            for (CreateQuestionnaireRequest.QuestionnaireSectionInputDto sectionDto : sections) {
                QuestionnaireSection section = new QuestionnaireSection();
                section.setSectionTitle(sectionDto.getSectionTitle());
                section.setSectionDescription(sectionDto.getSectionDescription());
                section.setOrderIndex(sectionDto.getOrderIndex() != null ? sectionDto.getOrderIndex() : 0);
                section.setEvaluateIndividuals(sectionDto.getEvaluateIndividuals() != null ? sectionDto.getEvaluateIndividuals() : false);
                section.setQuestionnaire(savedQuestionnaire);

                // Collect all items BEFORE saving section
                Set<QuestionnaireItem> itemsForSection = new HashSet<>();
                if (sectionDto.getItems() != null && !sectionDto.getItems().isEmpty()) {
                    for (int i = 0; i < sectionDto.getItems().size(); i++) {
                        QuestionnaireItem item = sectionDto.getItems().get(i).toEntity();
                        item.setQuestionnaire(savedQuestionnaire);
                        item.setSection(section);
                        item.setOrderIndex(i);
                        QuestionnaireItem savedItem = questionnaireItemRepository.save(item);
                        itemsForSection.add(savedItem);
                    }
                }
                
                // Set items collection BEFORE saving section
                section.setItems(itemsForSection);
                questionnaireSectionRepository.save(section);
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

        // Persist Google question/item ids to local rows for future updates and tracing.
        syncGoogleQuestionMappings(savedQuestionnaire.getId(), teacherId);

        userManagementService.asyncSyncAllDataToGoogleSheets(teacher.getEmail());

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
        return questionnaireRepository.findByAssignedClassesIdInAndIsActiveTrue(new ArrayList<>(classIds));
    }

    /**
     * Get questionnaires for a specific class
     */
    public List<Questionnaire> getQuestionnairesByClass(Long classId) {
        closeExpiredQuestionnaires();
        SchoolClass schoolClass = schoolClassRepository.findById(classId).orElse(null);
        List<Questionnaire> questionnaires = new ArrayList<>();
        if (schoolClass != null) {
            // Return ALL active questionnaires for this class regardless of target
            // Frontend will filter by target (ADVISER vs ADVISER_STUDENT vs STUDENT)
            questionnaires.addAll(questionnaireRepository
                .findByAssignedClassesContainingAndIsActiveTrue(schoolClass));
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
     * Hard delete questionnaire (preserves evaluations and scores by disconnecting FKs)
     */
    @Transactional
    public void deleteQuestionnaire(Long questionnaireId, Long teacherId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        // Verify teacher owns this questionnaire
        if (!questionnaire.getCreatedByTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("You can only delete your own questionnaires");
        }

        // Disconnect all evaluation scores from questionnaire items
        List<EvaluationScore> evaluationScores = evaluationScoreRepository.findByQuestionnaireId(questionnaireId);
        for (EvaluationScore score : evaluationScores) {
            score.setQuestionnaireItem(null);
        }
        evaluationScoreRepository.saveAll(evaluationScores);

        // Disconnect all student evaluation scores from questionnaire items
        List<StudentEvaluationScore> studentEvaluationScores = studentEvaluationScoreRepository.findByQuestionnaireId(questionnaireId);
        for (StudentEvaluationScore score : studentEvaluationScores) {
            score.setQuestionnaireItem(null);
        }
        studentEvaluationScoreRepository.saveAll(studentEvaluationScores);

        // Disconnect all evaluations from this questionnaire
        List<Evaluation> evaluations = evaluationRepository.findByQuestionnaireId(questionnaireId);
        for (Evaluation evaluation : evaluations) {
            evaluation.setQuestionnaire(null);
        }
        evaluationRepository.saveAll(evaluations);

        // Disconnect all student evaluations from this questionnaire
        List<StudentEvaluation> studentEvaluations = studentEvaluationRepository.findByQuestionnaireId(questionnaireId);
        for (StudentEvaluation studentEvaluation : studentEvaluations) {
            studentEvaluation.setQuestionnaire(null);
        }
        studentEvaluationRepository.saveAll(studentEvaluations);

        // Hard delete the questionnaire (cascades to items and sections)
        questionnaireRepository.deleteById(questionnaireId);

        log.info("Hard deleted questionnaire {} and disconnected {} evaluations, {} student evaluations, {} evaluation scores, and {} student evaluation scores",
                questionnaireId, evaluations.size(), studentEvaluations.size(), evaluationScores.size(), studentEvaluationScores.size());

        userManagementService.asyncSyncAllDataToGoogleSheets(questionnaire.getCreatedByTeacher().getEmail());
    }

    /**
     * Update questionnaire details (not the form itself)
     */
    @Transactional
    public Questionnaire updateQuestionnaire(Long questionnaireId, CreateQuestionnaireRequest request, Long teacherId) {
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

        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            questionnaire.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            questionnaire.setDescription(request.getDescription());
        }
        if (request.getTarget() != null) {
            try {
                questionnaire.setTarget(Questionnaire.QuestionnaireTarget.valueOf(request.getTarget().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Ignore invalid target
            }
        }
        
        // Update deadline if provided, else keep as is or clear if explicitly requested? 
        // We'll update it to the new value if provided in request, assuming the request passes the full state.
        if (request.getDeadlineAt() != null) {
            questionnaire.setDeadlineAt(normalizeAndValidateDeadline(request.getDeadlineAt()));
        } else {
            questionnaire.setDeadlineAt(null);
        }

        // Update sections first. We recreate section rows but preserve questionnaire item rows
        // (by id) whenever possible so local mappings stay stable across minor edits.
        List<QuestionnaireItem> existingItems = questionnaireItemRepository
                .findByQuestionnaireIdOrderByOrderIndex(questionnaireId);
        Map<Long, QuestionnaireItem> existingItemsById = existingItems.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(QuestionnaireItem::getId, item -> item, (a, b) -> a, HashMap::new));
        Set<Long> retainedItemIds = new HashSet<>();
        List<QuestionnaireItem> allSavedItems = new ArrayList<>();

        // Break section links before deleting sections to avoid cascading item deletions.
        // We want to preserve question rows (by id) for update semantics.
        for (QuestionnaireItem existingItem : existingItems) {
            existingItem.setSection(null);
        }
        questionnaireItemRepository.saveAll(existingItems);
        questionnaireItemRepository.flush();

        // Important: clear old section -> items collections in-memory before deleting
        // sections, otherwise JPA may still cascade-remove those items based on the
        // stale collection snapshot and mark them as deleted in this persistence context.
        for (QuestionnaireSection oldSection : questionnaire.getSections()) {
            oldSection.getItems().clear();
        }

        questionnaireSectionRepository.deleteAll(questionnaire.getSections());
        questionnaireSectionRepository.flush();
        questionnaire.getSections().clear();

        if (request.getSections() != null && !request.getSections().isEmpty()) {
            for (CreateQuestionnaireRequest.QuestionnaireSectionInputDto sectionDto : request.getSections()) {
                QuestionnaireSection section = new QuestionnaireSection();
                section.setSectionTitle(sectionDto.getSectionTitle());
                section.setSectionDescription(sectionDto.getSectionDescription());
                section.setOrderIndex(sectionDto.getOrderIndex() != null ? sectionDto.getOrderIndex() : 0);
                section.setEvaluateIndividuals(sectionDto.getEvaluateIndividuals() != null ? sectionDto.getEvaluateIndividuals() : false);
                section.setQuestionnaire(questionnaire);

                // Collect all items BEFORE saving section
                Set<QuestionnaireItem> itemsForSection = new HashSet<>();
                if (sectionDto.getItems() != null && !sectionDto.getItems().isEmpty()) {
                    for (int i = 0; i < sectionDto.getItems().size(); i++) {
                        CreateQuestionnaireRequest.QuestionnaireItemDto itemDto = sectionDto.getItems().get(i);
                        QuestionnaireItem item = findOrCreateQuestionnaireItem(itemDto.getId(), existingItemsById);
                        copyItemFields(itemDto, item, i, questionnaire, section);
                        QuestionnaireItem savedItem = questionnaireItemRepository.save(item);
                        allSavedItems.add(savedItem);
                        itemsForSection.add(savedItem);
                        if (savedItem.getId() != null) {
                            retainedItemIds.add(savedItem.getId());
                        }
                    }
                }
                
                // Set items collection BEFORE saving section
                section.setItems(itemsForSection);
                QuestionnaireSection savedSection = questionnaireSectionRepository.save(section);
                questionnaire.getSections().add(savedSection);
            }
        } else if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            for (int i = 0; i < request.getQuestions().size(); i++) {
                CreateQuestionnaireRequest.QuestionnaireItemDto itemDto = request.getQuestions().get(i);
                QuestionnaireItem item = findOrCreateQuestionnaireItem(itemDto.getId(), existingItemsById);
                copyItemFields(itemDto, item, i, questionnaire, null);
                QuestionnaireItem savedItem = questionnaireItemRepository.save(item);
                allSavedItems.add(savedItem);
                if (savedItem.getId() != null) {
                    retainedItemIds.add(savedItem.getId());
                }
            }
        }

        // Remove questions that are no longer present in the edited questionnaire payload.
        List<QuestionnaireItem> removedItems = existingItems.stream()
                .filter(item -> item.getId() != null && !retainedItemIds.contains(item.getId()))
                .toList();
        if (!removedItems.isEmpty()) {
            questionnaireItemRepository.deleteAll(removedItems);
        }

        questionnaire.setItems(new java.util.HashSet<>(allSavedItems));

        Questionnaire saved = questionnaireRepository.save(questionnaire);

        // Synchronize with Google Forms API
        if (saved.getGoogleFormId() != null) {
            // Build sync payload from the saved items we just persisted.
            // Do not rely on section.getItems() collections here, because they may be stale/empty
            // in-memory right after section recreation.
            List<QuestionnaireItem> allQuestions = new ArrayList<>(allSavedItems);
            allQuestions.sort(java.util.Comparator.comparing(QuestionnaireItem::getOrderIndex));

            List<QuestionnaireSection> allSections = new ArrayList<>(saved.getSections());
            allSections.sort(java.util.Comparator.comparing(QuestionnaireSection::getOrderIndex));

            Map<Long, List<QuestionnaireItem>> itemsBySectionId = allSavedItems.stream()
                    .filter(item -> item.getSection() != null && item.getSection().getId() != null)
                    .collect(Collectors.groupingBy(item -> item.getSection().getId()));

            for (QuestionnaireSection section : allSections) {
                List<QuestionnaireItem> sectionItems = new ArrayList<>(
                        itemsBySectionId.getOrDefault(section.getId(), List.of())
                );
                sectionItems.sort(java.util.Comparator.comparing(QuestionnaireItem::getOrderIndex));
                section.setItems(new java.util.HashSet<>(sectionItems));
            }

            List<QuestionnaireItem> looseQuestions = allQuestions.stream()
                    .filter(item -> item.getSection() == null)
                    .sorted(java.util.Comparator.comparing(QuestionnaireItem::getOrderIndex))
                    .toList();

            syncOrRebuildGoogleForm(saved, teacherId, looseQuestions, allSections);
        }

        log.info("Updated questionnaire {}", questionnaireId);

        userManagementService.asyncSyncAllDataToGoogleSheets(questionnaire.getCreatedByTeacher().getEmail());

        return saved;
    }

    private void syncOrRebuildGoogleForm(Questionnaire questionnaire,
                                         Long teacherId,
                                         List<QuestionnaireItem> looseQuestions,
                                         List<QuestionnaireSection> allSections) {
        try {
            googleFormsService.overwriteGoogleForm(
                    teacherId,
                    questionnaire.getGoogleFormId(),
                    questionnaire.getTitle(),
                    questionnaire.getDescription(),
                    looseQuestions,
                    allSections
            );
            syncGoogleQuestionMappings(questionnaire.getId(), teacherId);
            return;
        } catch (Exception overwriteError) {
            log.error("Overwrite sync failed for questionnaire {}. Attempting recovery with replacement form.",
                    questionnaire.getId(), overwriteError);
        }

        try {
            Form replacementForm;
            if (allSections != null && !allSections.isEmpty()) {
                replacementForm = googleFormsService.createGoogleFormWithSections(
                        teacherId,
                        questionnaire.getTitle(),
                        questionnaire.getDescription(),
                        looseQuestions != null ? looseQuestions : List.of(),
                        allSections
                );
            } else {
                replacementForm = googleFormsService.createGoogleForm(
                        teacherId,
                        questionnaire.getTitle(),
                        questionnaire.getDescription(),
                        looseQuestions != null ? looseQuestions : List.of()
                );
            }

            questionnaire.setGoogleFormId(replacementForm.getFormId());
            questionnaire.setGoogleFormUrl(replacementForm.getResponderUri());
            questionnaireRepository.save(questionnaire);
            syncGoogleQuestionMappings(questionnaire.getId(), teacherId);
            log.info("Recovered questionnaire {} by linking replacement Google Form {}",
                    questionnaire.getId(), replacementForm.getFormId());
        } catch (Exception rebuildError) {
            throw new RuntimeException("Failed to sync questionnaire to Google Form: " + rebuildError.getMessage(), rebuildError);
        }
    }

    private void syncGoogleQuestionMappings(Long questionnaireId, Long teacherId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));
        if (questionnaire.getGoogleFormId() == null || questionnaire.getGoogleFormId().isBlank()) {
            return;
        }

        Form form = googleFormsService.getFormById(teacherId, questionnaire.getGoogleFormId());
        if (form == null || form.getItems() == null || form.getItems().isEmpty()) {
            return;
        }

        // Build local order from repositories, not entity collections, to avoid stale
        // in-memory section->items associations after section recreation during update.
        List<QuestionnaireItem> allLocalItems = questionnaireItemRepository
                .findByQuestionnaireIdOrderByOrderIndex(questionnaireId);
        List<QuestionnaireSection> orderedSections = questionnaireSectionRepository
                .findByQuestionnaireIdOrderByOrderIndex(questionnaireId);

        List<QuestionnaireItem> localOrderedQuestions = allLocalItems.stream()
                .filter(item -> item.getSection() == null)
                .sorted(java.util.Comparator.comparing(QuestionnaireItem::getOrderIndex))
                .collect(Collectors.toCollection(ArrayList::new));

        Map<Long, List<QuestionnaireItem>> itemsBySectionId = allLocalItems.stream()
                .filter(item -> item.getSection() != null && item.getSection().getId() != null)
                .collect(Collectors.groupingBy(item -> item.getSection().getId()));

        for (QuestionnaireSection section : orderedSections) {
            List<QuestionnaireItem> sectionItems = new ArrayList<>(
                    itemsBySectionId.getOrDefault(section.getId(), List.of())
            );
            sectionItems.sort(java.util.Comparator.comparing(QuestionnaireItem::getOrderIndex));
            localOrderedQuestions.addAll(sectionItems);
        }

        List<Item> googleQuestionItems = form.getItems().stream()
                .filter(i -> i != null && i.getQuestionItem() != null && i.getQuestionItem().getQuestion() != null)
                .toList();

        int pairCount = Math.min(localOrderedQuestions.size(), googleQuestionItems.size());
        for (int i = 0; i < pairCount; i++) {
            QuestionnaireItem local = localOrderedQuestions.get(i);
            Item remote = googleQuestionItems.get(i);
            local.setGoogleFormItemId(remote.getItemId());
            local.setGoogleQuestionId(remote.getQuestionItem().getQuestion().getQuestionId());
        }
        questionnaireItemRepository.saveAll(localOrderedQuestions);
    }

    private QuestionnaireItem findOrCreateQuestionnaireItem(Long itemId, Map<Long, QuestionnaireItem> existingItemsById) {
        if (itemId == null) {
            return new QuestionnaireItem();
        }
        return existingItemsById.getOrDefault(itemId, new QuestionnaireItem());
    }

    private void copyItemFields(CreateQuestionnaireRequest.QuestionnaireItemDto source,
                                QuestionnaireItem target,
                                int orderIndex,
                                Questionnaire questionnaire,
                                QuestionnaireSection section) {
        target.setQuestionText(source.getQuestionText());
        target.setQuestionType(QuestionnaireItem.QuestionType.valueOf(source.getQuestionType()));
        target.setMaxScore(source.getMaxScore());
        target.setMinScore(source.getMinScore());
        target.setCorrectAnswer(source.getCorrectAnswer());
        target.setPointsValue(source.getPointsValue() != null ? source.getPointsValue() : 1);
        target.setRequired(source.getRequired() == null || source.getRequired());
        target.setOrderIndex(orderIndex);
        target.setQuestionnaire(questionnaire);
        target.setSection(section);
        if (source.getChoices() != null && !source.getChoices().isEmpty()) {
            try {
                target.setChoices(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(source.getChoices()));
            } catch (Exception e) {
                target.setChoices(null);
            }
        } else {
            target.setChoices(null);
        }
    }

    @Transactional
    public Questionnaire duplicateQuestionnaire(Long questionnaireId, Long teacherId) {
        Questionnaire original = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        if (!original.getCreatedByTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("You can only duplicate your own questionnaires");
        }

        List<QuestionnaireItem> looseQuestions = original.getItems().stream()
                .filter(item -> item.getSection() == null)
                .sorted(java.util.Comparator.comparing(QuestionnaireItem::getOrderIndex))
                .map(this::cloneQuestionnaireItem)
                .toList();

        List<CreateQuestionnaireRequest.QuestionnaireSectionInputDto> sections = original.getSections().stream()
                .sorted(java.util.Comparator.comparing(QuestionnaireSection::getOrderIndex))
                .map(section -> {
                    List<CreateQuestionnaireRequest.QuestionnaireItemDto> items = section.getItems().stream()
                            .sorted(java.util.Comparator.comparing(QuestionnaireItem::getOrderIndex))
                            .map(item -> {
                                CreateQuestionnaireRequest.QuestionnaireItemDto dto = new CreateQuestionnaireRequest.QuestionnaireItemDto();
                                dto.setQuestionText(item.getQuestionText());
                                dto.setOrderIndex(item.getOrderIndex());
                                dto.setQuestionType(item.getQuestionType().name());
                                dto.setMaxScore(item.getMaxScore());
                                dto.setMinScore(item.getMinScore());
                                dto.setCorrectAnswer(item.getCorrectAnswer());
                                dto.setPointsValue(item.getPointsValue());
                                dto.setRequired(item.getRequired() == null || item.getRequired());
                                if (item.getChoices() != null && !item.getChoices().isBlank()) {
                                    try {
                                        String[] parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                                                .readValue(item.getChoices(), String[].class);
                                        dto.setChoices(List.of(parsed));
                                    } catch (Exception ignored) {
                                        dto.setChoices(List.of());
                                    }
                                }
                                return dto;
                            })
                            .toList();
                    CreateQuestionnaireRequest.QuestionnaireSectionInputDto dto = new CreateQuestionnaireRequest.QuestionnaireSectionInputDto();
                    dto.setSectionTitle(section.getSectionTitle());
                    dto.setSectionDescription(section.getSectionDescription());
                    dto.setOrderIndex(section.getOrderIndex());
                    dto.setItems(items);
                    return dto;
                })
                .toList();

        String duplicateTitle = original.getTitle().endsWith(" (Copy)")
                ? original.getTitle()
                : original.getTitle() + " (Copy)";

        return createQuestionnaire(
                teacherId,
                duplicateTitle,
                original.getDescription(),
                looseQuestions,
                sections,
                original.getTarget() != null ? original.getTarget().name() : "ADVISER",
                null);
    }

    private QuestionnaireItem cloneQuestionnaireItem(QuestionnaireItem source) {
        QuestionnaireItem item = new QuestionnaireItem();
        item.setQuestionText(source.getQuestionText());
        item.setQuestionType(source.getQuestionType());
        item.setMaxScore(source.getMaxScore());
        item.setMinScore(source.getMinScore());
        item.setChoices(source.getChoices());
        item.setCorrectAnswer(source.getCorrectAnswer());
        item.setPointsValue(source.getPointsValue());
        item.setRequired(source.getRequired() == null || source.getRequired());
        item.setOrderIndex(source.getOrderIndex());
        return item;
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
