package group9.advisor_eval_system.service;

import group9.advisor_eval_system.entity.*;
import group9.advisor_eval_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

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

    /**
     * Get or create an evaluation for adviser + team + questionnaire
     */
    @Transactional
    public Evaluation getOrCreateEvaluation(Long adviserId, Long teamId, Long questionnaireId) {
        User adviser = userRepository.findById(adviserId)
                .orElseThrow(() -> new RuntimeException("Adviser not found"));

        if (adviser.getRole() != User.UserRole.ADVISER) {
            throw new RuntimeException("Only advisers can evaluate");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        // Check if adviser is assigned - use new ArrayList to avoid concurrent modification
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

        // Always ensure allowEdit is true for IN_PROGRESS evaluations
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

    /**
     * Save draft answers
     */
    @Transactional
    public Evaluation saveEvaluation(
            Long adviserId,
            Long evaluationId,
            Map<Long, Object> answers,
            String generalComments
    ) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found"));

        if (!evaluation.getAdviser().getId().equals(adviserId)) {
            throw new RuntimeException("Unauthorized evaluation access");
        }
        questionnaireService.ensureQuestionnaireOpenForResponses(evaluation.getQuestionnaire());

        // Only prevent editing if SUBMITTED or REVIEWED - if IN_PROGRESS, always allow
        if (evaluation.getStatus() == Evaluation.EvaluationStatus.IN_PROGRESS) {
            // Auto-correct allowEdit for IN_PROGRESS evaluations
            if (evaluation.getAllowEdit() == null || !evaluation.getAllowEdit()) {
                log.warn("Auto-correcting allowEdit for IN_PROGRESS evaluation {}", evaluationId);
                evaluation.setAllowEdit(true);
                evaluation = evaluationRepository.save(evaluation);
            }
        } else if (!evaluation.getAllowEdit()) {
            throw new RuntimeException("Evaluation editing is locked");
        }

        // Delete previous scores using repository
        evaluationScoreRepository.deleteByEvaluationId(evaluationId);

        // Create new scores
        for (Map.Entry<Long, Object> entry : answers.entrySet()) {
            Long itemId = entry.getKey();

            // Fetch the questionnaire item directly from repository
            QuestionnaireItem item = questionnaireItemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("Invalid questionnaire item"));

            EvaluationScore score = new EvaluationScore();
            score.setEvaluation(evaluation);
            score.setQuestionnaireItem(item);

            if (item.getQuestionType() == QuestionnaireItem.QuestionType.TEXT
                    || item.getQuestionType() == QuestionnaireItem.QuestionType.MULTIPLE_CHOICE) {
                score.setTextResponse(String.valueOf(entry.getValue()));
            } else {
                score.setNumericScore(Double.valueOf(entry.getValue().toString()));
            }

            evaluationScoreRepository.save(score);
        }

        evaluation.setGeneralComments(generalComments);
        Evaluation saved = evaluationRepository.save(evaluation);
        log.info("Successfully saved evaluation {} with allowEdit = {}", saved.getId(), saved.getAllowEdit());
        return saved;
    }

    /**
     * Submit evaluation (final)
     * Auto-grades the evaluation and locks the questionnaire if this is the first submission
     */
    @Transactional
    public Evaluation submitEvaluation(Long adviserId, Long evaluationId) {

        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found"));

        if (!evaluation.getAdviser().getId().equals(adviserId)) {
            throw new RuntimeException("Unauthorized evaluation submission");
        }
        questionnaireService.ensureQuestionnaireOpenForResponses(evaluation.getQuestionnaire());

        // Auto-grade all scores before submission
        try {
            autoGradeEvaluation(evaluation);
        } catch (Exception e) {
            log.error("Error during auto-grading: {}", e.getMessage(), e);
            // Continue with submission even if grading fails - don't let grading block submission
        }

        evaluation.setStatus(Evaluation.EvaluationStatus.SUBMITTED);
        evaluation.setSubmittedAt(LocalDateTime.now());
        evaluation.setAllowEdit(false);

        Evaluation submitted = evaluationRepository.save(evaluation);

        // Lock questionnaire if this is the first submission
        Questionnaire questionnaire = evaluation.getQuestionnaire();
        if (questionnaire != null && !Boolean.TRUE.equals(questionnaire.getIsLocked())) {
            questionnaire.setIsLocked(true);
            questionnaire.setLockedAt(LocalDateTime.now());
            questionnaireRepository.save(questionnaire);
            log.info("Auto-locked questionnaire {} on first evaluation submission", questionnaire.getId());
        }

        return submitted;
    }

    /**
     * Auto-grade all scores in an evaluation
     * Compares answers against correct answers and calculates points awarded
     */
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
                
                // Skip grading if no correct answer is defined
                if (correctAnswer == null || correctAnswer.trim().isEmpty()) {
                    score.setIsCorrect(null); // No grading criteria
                    score.setPointsAwarded(null);
                    continue;
                }

                // Determine if answer is correct based on question type
                boolean isCorrect = checkAnswer(item, score);
                score.setIsCorrect(isCorrect);

                // Award points if correct
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
                // Continue grading other scores even if one fails
            }
        }
    }

    /**
     * Check if an answer is correct based on question type
     */
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
                    // Case-insensitive text comparison
                    String textResponse = score.getTextResponse() != null ? 
                        score.getTextResponse().trim() : "";
                    return textResponse.equalsIgnoreCase(correctAnswer);

                case NUMERIC_SCALE:
                case RATING:
                    // Numeric comparison
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
                    // Exact string match for multiple choice
                    String response = score.getTextResponse() != null ? 
                        score.getTextResponse().trim() : "";
                    // Support both exact match and case-insensitive match
                    return response.equalsIgnoreCase(correctAnswer);

                default:
                    return false;
            }
        } catch (Exception e) {
            log.error("Error checking answer for item {}: {}", item.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get total score for an evaluation
     */
    public Integer getTotalScore(Long evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found"));

        return evaluation.getScores().stream()
                .mapToInt(score -> score.getPointsAwarded() != null ? score.getPointsAwarded() : 0)
                .sum();
    }

    /**
     * Get total possible points for a questionnaire
     */
    public Integer getTotalPossiblePoints(Long questionnaireId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        return questionnaire.getItems().stream()
                .mapToInt(item -> item.getPointsValue() != null ? item.getPointsValue() : 0)
                .sum();
    }
}
