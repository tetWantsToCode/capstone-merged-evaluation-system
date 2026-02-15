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

        if (team.getAdvisers().stream().noneMatch(a -> a.getId().equals(adviserId))) {
            throw new RuntimeException("Adviser not assigned to this team");
        }

        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

        Evaluation evaluation = evaluationRepository
                .findByTeamIdAndAdviserIdAndQuestionnaireId(teamId, adviserId, questionnaireId)
                .orElseGet(() -> {
                    Evaluation eval = new Evaluation();
                    eval.setAdviser(adviser);
                    eval.setTeam(team);
                    eval.setQuestionnaire(questionnaire);
                    eval.setStatus(Evaluation.EvaluationStatus.IN_PROGRESS);
                    eval.setAllowEdit(true);
                    return evaluationRepository.save(eval);
                });

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

        if (!evaluation.getAllowEdit()) {
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
        return evaluationRepository.save(evaluation);
    }

    /**
     * Submit evaluation (final)
     */
    @Transactional
    public Evaluation submitEvaluation(Long adviserId, Long evaluationId) {

        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found"));

        if (!evaluation.getAdviser().getId().equals(adviserId)) {
            throw new RuntimeException("Unauthorized evaluation submission");
        }

        evaluation.setStatus(Evaluation.EvaluationStatus.SUBMITTED);
        evaluation.setSubmittedAt(LocalDateTime.now());
        evaluation.setAllowEdit(false);

        return evaluationRepository.save(evaluation);
    }
}
