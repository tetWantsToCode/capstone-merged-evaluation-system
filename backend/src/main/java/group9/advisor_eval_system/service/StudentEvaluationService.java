package group9.advisor_eval_system.service;

import group9.advisor_eval_system.entity.*;
import group9.advisor_eval_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentEvaluationService {

    private final StudentEvaluationRepository studentEvaluationRepository;
    private final StudentEvaluationScoreRepository studentEvaluationScoreRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final StudentRepository studentRepository;
    private final QuestionnaireItemRepository questionnaireItemRepository;
    private final QuestionnaireService questionnaireService;

    public Student getStudentByEmail(String email) {
        return studentRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Student profile not found for email: " + email));
    }

    public List<Questionnaire> getAssignedQuestionnaires(Long studentId) {
        questionnaireService.closeExpiredQuestionnaires();
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<SchoolClass> classes = student.getClasses();
        if (classes == null || classes.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Questionnaire> allQuestionnaires = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (SchoolClass schoolClass : classes) {
            List<Questionnaire> classQuestionnaires = questionnaireRepository.findByAssignedClassesContainingAndTarget(
                    schoolClass,
                    Questionnaire.QuestionnaireTarget.STUDENT);

            for (Questionnaire q : classQuestionnaires) {
                boolean active = Boolean.TRUE.equals(q.getIsActive());
                boolean expiredByDeadline = q.getDeadlineAt() != null && !q.getDeadlineAt().isAfter(now);
                if (active || expiredByDeadline) {
                    allQuestionnaires.add(q);
                }
            }
        }

        return new ArrayList<>(allQuestionnaires);
    }

    public Optional<StudentEvaluation> findExistingStudentEvaluation(Long studentId, Long questionnaireId, Long evaluateeId) {
        if (evaluateeId != null) {
            return studentEvaluationRepository.findByStudentIdAndQuestionnaireIdAndEvaluateeId(studentId, questionnaireId, evaluateeId);
        }
        return studentEvaluationRepository.findByStudentIdAndQuestionnaireIdAndEvaluateeIsNull(studentId, questionnaireId);
    }

    @Transactional
    public StudentEvaluation getOrCreateStudentEvaluation(Long studentId, Long questionnaireId, Long evaluateeId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new RuntimeException("Questionnaire not found"));
        questionnaireService.ensureQuestionnaireOpenForResponses(questionnaire);

        Student evaluatee = null;
        if (evaluateeId != null) {
            evaluatee = studentRepository.findById(evaluateeId)
                    .orElseThrow(() -> new RuntimeException("Peer student not found"));
        }

        final Student finalEvaluatee = evaluatee;
        Optional<StudentEvaluation> existing;
        if (finalEvaluatee != null) {
            existing = studentEvaluationRepository.findByStudentIdAndQuestionnaireIdAndEvaluateeId(studentId, questionnaireId, finalEvaluatee.getId());
        } else {
            existing = studentEvaluationRepository.findByStudentIdAndQuestionnaireIdAndEvaluateeIsNull(studentId, questionnaireId);
        }

        return existing.orElseGet(() -> {
            StudentEvaluation eval = new StudentEvaluation();
            eval.setStudent(student);
            eval.setQuestionnaire(questionnaire);
            eval.setEvaluatee(finalEvaluatee);
            eval.setStatus(StudentEvaluation.EvaluationStatus.IN_PROGRESS);
            return studentEvaluationRepository.save(eval);
        });
    }

    @Transactional
    public StudentEvaluation saveEvaluation(Long studentId, Long evaluationId, Map<Long, Object> answers) {
        StudentEvaluation evaluation = studentEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found"));

        if (!evaluation.getStudent().getId().equals(studentId)) {
            throw new RuntimeException("Unauthorized: You cannot save someone else's evaluation");
        }
        questionnaireService.ensureQuestionnaireOpenForResponses(evaluation.getQuestionnaire());

        if (evaluation.getStatus() == StudentEvaluation.EvaluationStatus.SUBMITTED) {
            throw new RuntimeException("Evaluation is already submitted and cannot be edited");
        }

        // Delete old scores
        studentEvaluationScoreRepository.deleteByStudentEvaluationId(evaluationId);

        // Save new scores
        for (Map.Entry<Long, Object> entry : answers.entrySet()) {
            Long itemId = entry.getKey();
            QuestionnaireItem item = questionnaireItemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("Questionnaire item not found"));

            StudentEvaluationScore score = new StudentEvaluationScore();
            score.setStudentEvaluation(evaluation);
            score.setQuestionnaireItem(item);

            if (item.getQuestionType() == QuestionnaireItem.QuestionType.TEXT ||
                item.getQuestionType() == QuestionnaireItem.QuestionType.MULTIPLE_CHOICE) {
                score.setTextResponse(String.valueOf(entry.getValue()));
            } else {
                try {
                    score.setNumericScore(Double.valueOf(entry.getValue().toString()));
                } catch (NumberFormatException ignored) {}
            }

            studentEvaluationScoreRepository.save(score);
        }

        return studentEvaluationRepository.save(evaluation);
    }

    @Transactional
    public void saveGroupEvaluation(Long studentId, Map<Long, Map<Long, Object>> groupAnswers) {
        for (Map.Entry<Long, Map<Long, Object>> entry : groupAnswers.entrySet()) {
            saveEvaluation(studentId, entry.getKey(), entry.getValue());
        }
    }

    @Transactional
    public void submitGroupEvaluations(Long studentId, List<Long> evaluationIds) {
        for (Long id : evaluationIds) {
            submitEvaluation(studentId, id);
        }
    }

    @Transactional
    public StudentEvaluation submitEvaluation(Long studentId, Long evaluationId) {
        StudentEvaluation evaluation = studentEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new RuntimeException("Evaluation not found"));

        if (!evaluation.getStudent().getId().equals(studentId)) {
            throw new RuntimeException("Unauthorized: You cannot submit someone else's evaluation");
        }
        questionnaireService.ensureQuestionnaireOpenForResponses(evaluation.getQuestionnaire());

        // Auto Grade
        autoGradeEvaluation(evaluation);

        evaluation.setStatus(StudentEvaluation.EvaluationStatus.SUBMITTED);
        evaluation.setSubmittedAt(LocalDateTime.now());
        
        return studentEvaluationRepository.save(evaluation);
    }

    private void autoGradeEvaluation(StudentEvaluation evaluation) {
        // Fetch all scores related to this evaluation directly using the repository
        List<StudentEvaluationScore> scores = studentEvaluationScoreRepository.findByStudentEvaluationId(evaluation.getId());
        if (scores == null || scores.isEmpty()) return;

        for (StudentEvaluationScore score : scores) {
            try {
                QuestionnaireItem item = score.getQuestionnaireItem();
                if (item == null) continue;

                String correctAnswer = item.getCorrectAnswer();
                if (correctAnswer == null || correctAnswer.trim().isEmpty()) {
                    score.setIsCorrect(null);
                    score.setPointsAwarded(null);
                    studentEvaluationScoreRepository.save(score);
                    continue;
                }

                boolean isCorrect = checkAnswer(item, score);
                score.setIsCorrect(isCorrect);
                if (isCorrect) {
                    score.setPointsAwarded(item.getPointsValue() != null ? item.getPointsValue() : 1);
                } else {
                    score.setPointsAwarded(0);
                }
                studentEvaluationScoreRepository.save(score);
            } catch (Exception e) {
                log.error("Error auto-grading student score", e);
            }
        }
    }

    private boolean checkAnswer(QuestionnaireItem item, StudentEvaluationScore score) {
        try {
            String correctAnswer = item.getCorrectAnswer();
            if (correctAnswer == null || correctAnswer.trim().isEmpty()) return false;
            correctAnswer = correctAnswer.trim();

            switch (item.getQuestionType()) {
                case TEXT:
                case MULTIPLE_CHOICE:
                    String response = score.getTextResponse() != null ? score.getTextResponse().trim() : "";
                    return response.equalsIgnoreCase(correctAnswer);
                case NUMERIC_SCALE:
                case RATING:
                    Double numericScore = score.getNumericScore();
                    Double correctNumeric = Double.parseDouble(correctAnswer);
                    return numericScore != null && numericScore.equals(correctNumeric);
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }


    public group9.advisor_eval_system.dto.StudentReportSummaryDto getStudentReportSummary(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        String teamName = "No Team";
        if (student.getTeamStudents() != null && !student.getTeamStudents().isEmpty()) {
            teamName = student.getTeamStudents().get(0).getTeam().getName();
        }

        List<Questionnaire> questionnaires = getAssignedQuestionnaires(studentId);
        List<group9.advisor_eval_system.dto.StudentReportSummaryDto.QuestionnaireSummary> summaries = new ArrayList<>();

        for (Questionnaire q : questionnaires) {
            // Find all peer evaluations WHERE this student is the evaluatee
            List<StudentEvaluation> peerEvals = studentEvaluationRepository.findByQuestionnaireIdAndEvaluateeId(q.getId(), studentId);
            
            // Filter submitted only
            List<StudentEvaluation> submittedPeerEvals = peerEvals.stream()
                    .filter(e -> e.getStatus() == StudentEvaluation.EvaluationStatus.SUBMITTED)
                    .collect(Collectors.toList());

            if (submittedPeerEvals.isEmpty()) continue;

            // Calculate aggregates
            Map<String, List<Double>> categoryScores = new HashMap<>();
            List<String> comments = new ArrayList<>();

            for (StudentEvaluation eval : submittedPeerEvals) {
                if (eval.getScores() != null) {
                    for (StudentEvaluationScore score : eval.getScores()) {
                        QuestionnaireItem item = score.getQuestionnaireItem();
                        if (item == null) continue;

                        String category = item.getSection() != null ? item.getSection().getSectionTitle() : "General";
                        
                        if (score.getNumericScore() != null) {
                            categoryScores.computeIfAbsent(category, k -> new ArrayList<>()).add(score.getNumericScore());
                        }
                        if (score.getTextResponse() != null && !score.getTextResponse().trim().isEmpty()) {
                            comments.add(score.getTextResponse());
                        }
                    }
                }
            }

            List<group9.advisor_eval_system.dto.StudentReportSummaryDto.CategoryScore> catScores = categoryScores.entrySet().stream()
                    .map(entry -> group9.advisor_eval_system.dto.StudentReportSummaryDto.CategoryScore.builder()
                            .categoryName(entry.getKey())
                            .averageScore(entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0))
                            .responseCount(entry.getValue().size())
                            .build())
                    .collect(Collectors.toList());

            double overallAvg = catScores.stream().mapToDouble(c -> c.getAverageScore()).average().orElse(0.0);

            summaries.add(group9.advisor_eval_system.dto.StudentReportSummaryDto.QuestionnaireSummary.builder()
                    .questionnaireId(q.getId())
                    .questionnaireTitle(q.getTitle())
                    .overallAverage(Math.round(overallAvg * 100.0) / 100.0)
                    .categoryScores(catScores)
                    .feedbackComments(comments)
                    .build());
        }

        return group9.advisor_eval_system.dto.StudentReportSummaryDto.builder()
                .studentName(student.getFirstName() + " " + student.getLastName())
                .teamName(teamName)
                .summaries(summaries)
                .build();
    }
}
