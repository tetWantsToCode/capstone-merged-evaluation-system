package group9.advisor_eval_system.service;

import group9.advisor_eval_system.entity.*;
import group9.advisor_eval_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final EvaluationScoreRepository evaluationScoreRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final QuestionnaireItemRepository questionnaireItemRepository;
    private final QuestionnaireService questionnaireService;
    private final StudentRepository studentRepository;
    private final StudentEvaluationRepository studentEvaluationRepository;
    private final StudentEvaluationScoreRepository studentEvaluationScoreRepository;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private UserManagementService userManagementService;

    // ─────────────────────────────────────────────────────────────
    // Existing team-level evaluation methods (unchanged)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Evaluation getOrCreateEvaluation(Long adviserId, Long teamId, Long questionnaireId) {
        User adviser = userRepository.findById(adviserId)
                .orElseThrow(() -> new RuntimeException("Adviser not found"));

        if (adviser.getRole() != User.UserRole.ADVISER) {
            throw new RuntimeException("Only advisers can evaluate");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        if (new ArrayList<>(team.getAdvisers()).stream().noneMatch(a -> a.getId().equals(adviserId))) {
            throw new RuntimeException("Adviser not assigned to this team");
        }

        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));
        questionnaireService.ensureQuestionnaireOpenForResponses(questionnaire);

        Evaluation evaluation = evaluationRepository
                .findByTeamIdAndAdviserIdAndQuestionnaireId(teamId, adviserId, questionnaireId)
                .orElseGet(() -> {
                    Evaluation eval = new Evaluation();
                    eval.setAdviser(adviser);
                    eval.setTeam(team);
                    eval.setQuestionnaire(questionnaire);
                    eval.setStatus(Evaluation.EvaluationStatus.IN_PROGRESS);
                    eval.setAllowEdit(true);
                    log.info("Creating new evaluation with allowEdit = true");
                    return evaluationRepository.save(eval);
                });

        if (evaluation.getStatus() == Evaluation.EvaluationStatus.IN_PROGRESS) {
            if (evaluation.getAllowEdit() == null || !evaluation.getAllowEdit()) {
                log.warn("Resetting allowEdit to true for IN_PROGRESS evaluation {}", evaluation.getId());
                evaluation.setAllowEdit(true);
                evaluation = evaluationRepository.save(evaluation);
            }
        }

        log.info("Evaluation {} status: {}, allowEdit: {}", evaluation.getId(), evaluation.getStatus(), evaluation.getAllowEdit());
        return evaluation;
    }

    @Transactional
    public Evaluation saveEvaluation(
            Long adviserId,
            Long evaluationId,
            Map<Long, Object> answers,
            String generalComments) {

        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found"));

        if (!evaluation.getAdviser().getId().equals(adviserId)) {
            throw new RuntimeException("Unauthorized evaluation access");
        }
        questionnaireService.ensureQuestionnaireOpenForResponses(evaluation.getQuestionnaire());

        if (evaluation.getStatus() == Evaluation.EvaluationStatus.IN_PROGRESS) {
            if (evaluation.getAllowEdit() == null || !evaluation.getAllowEdit()) {
                log.warn("Auto-correcting allowEdit for IN_PROGRESS evaluation {}", evaluationId);
                evaluation.setAllowEdit(true);
                evaluation = evaluationRepository.save(evaluation);
            }
        } else if (!evaluation.getAllowEdit()) {
            throw new RuntimeException("Evaluation editing is locked");
        }

        evaluationScoreRepository.deleteByEvaluationId(evaluationId);

        for (Map.Entry<Long, Object> entry : answers.entrySet()) {
            Long itemId = entry.getKey();

            QuestionnaireItem item = questionnaireItemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("Invalid questionnaire item"));

            EvaluationScore score = new EvaluationScore();
            score.setEvaluation(evaluation);
            score.setQuestionnaireItem(item);

            Object value = entry.getValue();
            if (value == null) {
                log.warn("Null value for question item {}", itemId);
                continue;
            }

            // Skip if value is a Map (likely nested/student answers, not for team-level evaluation)
            if (value instanceof Map) {
                log.warn("Skipping Map value for question item {} - likely nested answers structure", itemId);
                continue;
            }

            if (item.getQuestionType() == QuestionnaireItem.QuestionType.TEXT
                    || item.getQuestionType() == QuestionnaireItem.QuestionType.MULTIPLE_CHOICE) {
                score.setTextResponse(String.valueOf(value));
            } else {
                // Numeric type
                try {
                    double numValue;
                    if (value instanceof Number) {
                        numValue = ((Number) value).doubleValue();
                    } else {
                        String strValue = String.valueOf(value).trim();
                        if (strValue.isEmpty()) {
                            log.warn("Empty string value for numeric question item {}", itemId);
                            continue;
                        }
                        numValue = Double.parseDouble(strValue);
                    }
                    score.setNumericScore(numValue);
                } catch (NumberFormatException e) {
                    log.error("Cannot parse numeric value '{}' for question item {}: {}", value, itemId, e.getMessage(), e);
                    throw new RuntimeException("Invalid numeric value for question " + itemId + ": " + value);
                }
            }

            evaluationScoreRepository.save(score);
        }

        evaluation.setGeneralComments(generalComments);
        Evaluation saved = evaluationRepository.save(evaluation);
        log.info("Successfully saved evaluation {} with allowEdit = {}", saved.getId(), saved.getAllowEdit());
        return saved;
    }

    @Transactional
    public Evaluation submitEvaluation(Long adviserId, Long evaluationId) {

        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found"));

        if (!evaluation.getAdviser().getId().equals(adviserId)) {
            throw new RuntimeException("Unauthorized evaluation submission");
        }
        questionnaireService.ensureQuestionnaireOpenForResponses(evaluation.getQuestionnaire());

        try {
            autoGradeEvaluation(evaluation);
        } catch (Exception e) {
            log.error("Error during auto-grading: {}", e.getMessage(), e);
        }

        evaluation.setStatus(Evaluation.EvaluationStatus.SUBMITTED);
        evaluation.setSubmittedAt(LocalDateTime.now());
        evaluation.setAllowEdit(false);

        Evaluation submitted = evaluationRepository.save(evaluation);

        Questionnaire questionnaire = evaluation.getQuestionnaire();
        if (questionnaire != null && !Boolean.TRUE.equals(questionnaire.getIsLocked())) {
            questionnaire.setIsLocked(true);
            questionnaire.setLockedAt(LocalDateTime.now());
            questionnaireRepository.save(questionnaire);
            log.info("Auto-locked questionnaire {} on first evaluation submission", questionnaire.getId());
        }

        if (evaluation.getTeam() != null && evaluation.getTeam().getSchoolClass() != null 
            && evaluation.getTeam().getSchoolClass().getTeacher() != null) {
            userManagementService.asyncSyncAllDataToGoogleSheets(
                evaluation.getTeam().getSchoolClass().getTeacher().getEmail()
            );
        }

        return submitted;
    }

    private void autoGradeEvaluation(Evaluation evaluation) {
        Set<EvaluationScore> scores = evaluation.getScores();

        if (scores == null || scores.isEmpty()) {
            log.debug("No scores to grade for evaluation {}", evaluation.getId());
            return;
        }

        for (EvaluationScore score : scores) {
            try {
                QuestionnaireItem item = score.getQuestionnaireItem();

                if (item == null) {
                    log.warn("QuestionnaireItem is null for score {}", score.getId());
                    continue;
                }

                String correctAnswer = item.getCorrectAnswer();

                if (correctAnswer == null || correctAnswer.trim().isEmpty()) {
                    score.setIsCorrect(null);
                    score.setPointsAwarded(null);
                    continue;
                }

                boolean isCorrect = checkAnswer(item, score);
                score.setIsCorrect(isCorrect);

                if (isCorrect) {
                    int pointsValue = item.getPointsValue() != null ? item.getPointsValue() : 1;
                    score.setPointsAwarded(pointsValue);
                } else {
                    score.setPointsAwarded(0);
                }

                evaluationScoreRepository.save(score);
                log.debug("Auto-graded score {} - isCorrect: {}, pointsAwarded: {}",
                        score.getId(), isCorrect, score.getPointsAwarded());

            } catch (Exception e) {
                log.warn("Error grading score {}: {}", score.getId(), e.getMessage());
            }
        }
    }

    private boolean checkAnswer(QuestionnaireItem item, EvaluationScore score) {
        try {
            String correctAnswer = item.getCorrectAnswer();
            if (correctAnswer == null || correctAnswer.isEmpty()) {
                return false;
            }

            correctAnswer = correctAnswer.trim();
            QuestionnaireItem.QuestionType type = item.getQuestionType();

            switch (type) {
                case TEXT:
                    String textResponse = score.getTextResponse() != null ?
                            score.getTextResponse().trim() : "";
                    return textResponse.equalsIgnoreCase(correctAnswer);

                case NUMERIC_SCALE:
                case RATING:
                    try {
                        Double numericScore = score.getNumericScore();
                        Double correctNumeric = Double.parseDouble(correctAnswer);
                        return numericScore != null && numericScore.equals(correctNumeric);
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse numeric answer for item {}: expected {}, got {}",
                                item.getId(), correctAnswer, score.getNumericScore());
                        return false;
                    }

                case MULTIPLE_CHOICE:
                    String response = score.getTextResponse() != null ?
                            score.getTextResponse().trim() : "";
                    return response.equalsIgnoreCase(correctAnswer);

                default:
                    return false;
            }
        } catch (Exception e) {
            log.error("Error checking answer for item {}: {}", item.getId(), e.getMessage(), e);
            return false;
        }
    }

    public Integer getTotalScore(Long evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found"));

        return evaluation.getScores().stream()
                .mapToInt(score -> score.getPointsAwarded() != null ? score.getPointsAwarded() : 0)
                .sum();
    }

    public Integer getTotalPossiblePoints(Long questionnaireId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        return questionnaire.getItems().stream()
                .mapToInt(item -> item.getPointsValue() != null ? item.getPointsValue() : 0)
                .sum();
    }

    // ─────────────────────────────────────────────────────────────
    // New adviser-to-student individual evaluation methods
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public StudentEvaluation getOrCreateAdviserStudentEvaluation(
            Long adviserId, Long teamId, Long studentId, Long questionnaireId) {

        User adviser = userRepository.findById(adviserId)
                .orElseThrow(() -> new RuntimeException("Adviser not found"));

        if (adviser.getRole() != User.UserRole.ADVISER) {
            throw new RuntimeException("Only advisers can use this evaluation type");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        if (new ArrayList<>(team.getAdvisers()).stream().noneMatch(a -> a.getId().equals(adviserId))) {
            throw new RuntimeException("Adviser not assigned to this team");
        }

        boolean studentInTeam = team.getTeamStudents().stream()
                .anyMatch(ts -> ts.getStudent().getId().equals(studentId));
        if (!studentInTeam) {
            throw new RuntimeException("Student is not a member of this team");
        }

        Student evaluatee = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));
        questionnaireService.ensureQuestionnaireOpenForResponses(questionnaire);

        return studentEvaluationRepository
                .findByAdviserIdAndEvaluateeIdAndQuestionnaireIdAndTeamId(
                        adviserId, studentId, questionnaireId, teamId)
                .orElseGet(() -> {
                    StudentEvaluation eval = new StudentEvaluation();
                    eval.setAdviser(adviser);
                    eval.setTeam(team);
                    eval.setEvaluatee(evaluatee);
                    eval.setStudent(null);
                    eval.setQuestionnaire(questionnaire);
                    eval.setStatus(StudentEvaluation.EvaluationStatus.IN_PROGRESS);
                    log.info("Creating new adviser-student evaluation: adviser={}, student={}, team={}",
                            adviserId, studentId, teamId);
                    return studentEvaluationRepository.save(eval);
                });
    }

    @Transactional
    public StudentEvaluation saveAdviserStudentEvaluation(
            Long adviserId, Long evaluationId, Map<Long, Object> answers) {

        StudentEvaluation evaluation = studentEvaluationRepository.findByIdWithDetails(evaluationId)
                .orElseThrow(() -> new RuntimeException("Student evaluation not found"));

        if (evaluation.getAdviser() == null || !evaluation.getAdviser().getId().equals(adviserId)) {
            throw new RuntimeException("Unauthorized: This evaluation does not belong to you");
        }
        if (evaluation.getStatus() == StudentEvaluation.EvaluationStatus.SUBMITTED) {
            throw new RuntimeException("Cannot edit a submitted evaluation");
        }
        questionnaireService.ensureQuestionnaireOpenForResponses(evaluation.getQuestionnaire());

        studentEvaluationScoreRepository.deleteByStudentEvaluationId(evaluationId);

        for (Map.Entry<Long, Object> entry : answers.entrySet()) {
            QuestionnaireItem item = questionnaireItemRepository.findById(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Invalid questionnaire item: " + entry.getKey()));

            StudentEvaluationScore score = new StudentEvaluationScore();
            score.setStudentEvaluation(evaluation);
            score.setQuestionnaireItem(item);

            Object value = entry.getValue();
            if (value == null) {
                log.warn("Null value for question item {}", entry.getKey());
                continue;
            }

            // Skip if value is a Map (likely nested/student answers, not for individual evaluation)
            if (value instanceof Map) {
                log.warn("Skipping Map value for question item {} - likely nested answers structure", entry.getKey());
                continue;
            }

            if (item.getQuestionType() == QuestionnaireItem.QuestionType.TEXT
                    || item.getQuestionType() == QuestionnaireItem.QuestionType.MULTIPLE_CHOICE) {
                score.setTextResponse(String.valueOf(value));
            } else {
                // Numeric type
                try {
                    double numValue;
                    if (value instanceof Number) {
                        numValue = ((Number) value).doubleValue();
                    } else {
                        String strValue = String.valueOf(value).trim();
                        if (strValue.isEmpty()) {
                            log.warn("Empty string value for numeric question item {}", entry.getKey());
                            continue;
                        }
                        numValue = Double.parseDouble(strValue);
                    }
                    score.setNumericScore(numValue);
                } catch (NumberFormatException e) {
                    log.error("Cannot parse numeric value '{}' for question item {}: {}", value, entry.getKey(), e.getMessage(), e);
                    throw new RuntimeException("Invalid numeric value for question " + entry.getKey() + ": " + value);
                }
            }
            studentEvaluationScoreRepository.save(score);
        }

        log.info("Saved adviser-student evaluation {}", evaluationId);
        return studentEvaluationRepository.findById(evaluationId).orElseThrow();
    }

    @Transactional
    public StudentEvaluation submitAdviserStudentEvaluation(Long adviserId, Long evaluationId) {

        StudentEvaluation evaluation = studentEvaluationRepository.findByIdWithDetails(evaluationId)
                .orElseThrow(() -> new RuntimeException("Student evaluation not found"));

        if (evaluation.getAdviser() == null || !evaluation.getAdviser().getId().equals(adviserId)) {
            throw new RuntimeException("Unauthorized: This evaluation does not belong to you");
        }
        if (evaluation.getStatus() == StudentEvaluation.EvaluationStatus.SUBMITTED) {
            throw new RuntimeException("Evaluation already submitted");
        }
        questionnaireService.ensureQuestionnaireOpenForResponses(evaluation.getQuestionnaire());

        evaluation.setStatus(StudentEvaluation.EvaluationStatus.SUBMITTED);
        evaluation.setSubmittedAt(LocalDateTime.now());
        StudentEvaluation submitted = studentEvaluationRepository.save(evaluation);
        log.info("Submitted adviser-student evaluation {}", evaluationId);

        if (evaluation.getTeam() != null && evaluation.getTeam().getSchoolClass() != null 
            && evaluation.getTeam().getSchoolClass().getTeacher() != null) {
            userManagementService.asyncSyncAllDataToGoogleSheets(
                evaluation.getTeam().getSchoolClass().getTeacher().getEmail()
            );
        }

        return submitted;
    }
}