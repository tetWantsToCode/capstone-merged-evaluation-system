package group9.advisor_eval_system.apeer.service;

import group9.advisor_eval_system.apeer.dto.EvaluationSubmissionRequest;
import group9.advisor_eval_system.apeer.dto.MemberResponse;
import group9.advisor_eval_system.apeer.entity.EvaluationActivity;
import group9.advisor_eval_system.apeer.entity.PeerEvaluation;
import group9.advisor_eval_system.apeer.entity.PeerEvaluationComment;
import group9.advisor_eval_system.apeer.repository.PeerEvaluationCommentRepository;
import group9.advisor_eval_system.apeer.repository.PeerEvaluationRepository;
import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.StudentRepository;
import group9.advisor_eval_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PeerEvaluationService {

    private final EvaluationActivityService activityService;
    private final PeerEvaluationRepository peerEvaluationRepository;
    private final PeerEvaluationCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ScoreValidator scoreValidator;

    /**
     * Returns ordered list of members (students) for this activity — students in the activity's class.
     * SRS 5.2: "Student views assigned group members (ordered by Excel upload)."
     */
    public List<MemberResponse> getMembersForActivity(Long activityId) {
        EvaluationActivity activity = activityService.getEntityById(activityId);
        if (activity.getSchoolClass() == null) {
            return List.of();
        }
        List<Student> students = studentRepository.findByClassesId(activity.getSchoolClass().getId());
        List<MemberResponse> result = new ArrayList<>();
        int index = 1;
        for (Student s : students) {
            result.add(new MemberResponse(
                    s.getId(),
                    s.getStudentId(),
                    s.getFirstName(),
                    s.getLastName(),
                    s.getEmail(),
                    index++
            ));
        }
        return result;
    }

    @Transactional
    public void submit(EvaluationSubmissionRequest request) {
        User evaluator = userRepository.findById(request.getEvaluatorId())
                .orElseThrow(() -> new RuntimeException("Evaluator not found"));
        Student target = studentRepository.findById(request.getTargetStudentId())
                .orElseThrow(() -> new RuntimeException("Target student not found"));
        EvaluationActivity activity = activityService.getEntityById(request.getActivityId());

        if (activity.getDeadline() != null && LocalDateTime.now().isAfter(activity.getDeadline())) {
            throw new RuntimeException("Submission deadline has passed. This evaluation is no longer accepting responses.");
        }

        List<String> criteria = activity.getRubricCriteria() != null ? activity.getRubricCriteria() : List.of();
        ScoreValidator.ValidationResult vr = scoreValidator.validate(request.getRubricScores(), criteria);
        if (!vr.isValid()) {
            throw new RuntimeException("Validation failed: " + vr.getErrorMessage());
        }

        // Unique score per criterion across all targets for this evaluator in this activity (SRS 5.2)
        List<PeerEvaluation> existing = peerEvaluationRepository.findByActivityAndEvaluator(activity, evaluator);
        for (String criterion : criteria) {
            Integer newScore = request.getRubricScores().get(criterion);
            if (newScore == null) continue;
            for (PeerEvaluation e : existing) {
                if (e.getTargetStudent().getId().equals(target.getId())) continue; // same target, will update or skip
                Map<String, Integer> existingScores = e.getRubricScores();
                if (existingScores != null && newScore.equals(existingScores.get(criterion))) {
                    throw new RuntimeException("Duplicate score " + newScore + " for criterion '" + criterion + "'. Each member must receive a unique score per criterion.");
                }
            }
        }

        PeerEvaluation evaluation = peerEvaluationRepository
                .findByActivityIdAndEvaluatorIdAndTargetStudentId(activity.getId(), evaluator.getId(), target.getId())
                .orElse(null);
        if (evaluation == null) {
            evaluation = new PeerEvaluation();
            evaluation.setActivity(activity);
            evaluation.setEvaluator(evaluator);
            evaluation.setTargetStudent(target);
        }
        evaluation.setRubricScores(request.getRubricScores());
        evaluation.setCommentContent(request.getCommentContent() != null ? request.getCommentContent().trim() : "");
        evaluation.setSubmittedAt(LocalDateTime.now());
        evaluation = peerEvaluationRepository.save(evaluation);

        // Save comment (no AI tagging)
        PeerEvaluationComment comment = commentRepository.findByEvaluation(evaluation).orElse(new PeerEvaluationComment());
        comment.setEvaluation(evaluation);
        comment.setContent(evaluation.getCommentContent());
        comment.setAiTag(null);
        comment.setTaggedAt(null);
        commentRepository.save(comment);
    }

    public Map<String, Object> getSubmissionStatus(Long activityId, Long evaluatorId) {
        Map<String, Object> result = new HashMap<>();
        List<MemberResponse> members = getMembersForActivity(activityId);
        result.put("totalMembers", members.size());
        result.put("submittedCount", 0);
        result.put("complete", false);
        if (evaluatorId == null || members.isEmpty()) {
            return result;
        }
        EvaluationActivity activity = activityService.getEntityById(activityId);
        User evaluator = userRepository.findById(evaluatorId).orElse(null);
        if (evaluator == null) return result;
        List<PeerEvaluation> submitted = peerEvaluationRepository.findByActivityAndEvaluator(activity, evaluator);
        result.put("submittedCount", submitted.size());
        result.put("complete", submitted.size() >= members.size());
        return result;
    }
}
