package group9.advisor_eval_system.apeer.controller;

import group9.advisor_eval_system.apeer.dto.EvaluationSubmissionRequest;
import group9.advisor_eval_system.apeer.dto.MemberResponse;
import group9.advisor_eval_system.apeer.entity.PeerEvaluation;
import group9.advisor_eval_system.apeer.repository.PeerEvaluationCommentRepository;
import group9.advisor_eval_system.apeer.repository.PeerEvaluationRepository;
import group9.advisor_eval_system.apeer.service.EvaluationActivityService;
import group9.advisor_eval_system.apeer.service.PeerEvaluationService;
import group9.advisor_eval_system.apeer.entity.EvaluationActivity;
import group9.advisor_eval_system.util.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * APEER: Get activity members and submit peer evaluations. SRS 5.2.
 * GET /api/apeer/activities/{id}/members, POST /api/apeer/evaluations/submit.
 */
@RestController
@RequestMapping("/api/apeer")
@RequiredArgsConstructor
public class ApeerSubmissionController {

    private final PeerEvaluationService peerEvaluationService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PeerEvaluationRepository peerEvaluationRepository;
    private final PeerEvaluationCommentRepository commentRepository;
    private final EvaluationActivityService activityService;

    /**
     * GET /api/apeer/activities/{id}/summary
     * Returns per-student aggregated scores for all submissions in an activity.
     * Used by the teacher drill-down view. SRS REQ-507.
     */
    @GetMapping("/activities/{activityId}/summary")
    public ResponseEntity<?> getActivitySummary(@PathVariable Long activityId) {
        try {
            EvaluationActivity activity = activityService.getEntityById(activityId);
            List<PeerEvaluation> evals = peerEvaluationRepository.findByActivityId(activityId);

            Map<Long, Map<String, Object>> byStudent = new LinkedHashMap<>();
            for (PeerEvaluation eval : evals) {
                if (eval.getTargetStudent() == null) continue;
                Long studentId = eval.getTargetStudent().getId();
                Map<String, Object> entry = byStudent.computeIfAbsent(studentId, k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    String name = (eval.getTargetStudent().getFirstName() != null ? eval.getTargetStudent().getFirstName() : "")
                            + " " + (eval.getTargetStudent().getLastName() != null ? eval.getTargetStudent().getLastName() : "");
                    m.put("studentId", studentId);
                    m.put("studentName", name.trim());
                    m.put("email", eval.getTargetStudent().getEmail());
                    m.put("evaluationCount", 0);
                    m.put("totalScore", 0.0);
                    m.put("scoresByCriteria", new LinkedHashMap<String, Double>());
                    return m;
                });

                entry.put("evaluationCount", (int) entry.get("evaluationCount") + 1);

                Map<String, Integer> rubricScores = eval.getRubricScores();
                if (rubricScores != null) {
                    int rowTotal = rubricScores.values().stream().mapToInt(Integer::intValue).sum();
                    entry.put("totalScore", (double) entry.get("totalScore") + rowTotal);

                    @SuppressWarnings("unchecked")
                    Map<String, Double> byCriteria = (Map<String, Double>) entry.get("scoresByCriteria");
                    rubricScores.forEach((criterion, score) ->
                            byCriteria.merge(criterion, score.doubleValue(), (a, b) -> a + b));
                }

                commentRepository.findByEvaluationId(eval.getId()).ifPresent(c -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> comments = (List<Map<String, Object>>) entry.computeIfAbsent("comments", k -> new ArrayList<>());
                    if (c.getContent() != null && !c.getContent().isBlank()) {
                        Map<String, Object> commentObj = new LinkedHashMap<>();
                        commentObj.put("content", c.getContent());
                        commentObj.put("aiTag", c.getAiTag());
                        comments.add(commentObj);
                    }
                });
            }

            List<Map<String, Object>> result = new ArrayList<>(byStudent.values());
            result.sort(Comparator.comparingDouble(m -> -((Double) m.get("totalScore"))));
            return ResponseEntity.ok(Map.of("activityTitle", activity.getTitle(), "students", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/activities/{activityId}/members")
    public ResponseEntity<List<MemberResponse>> getMembers(@PathVariable Long activityId) {
        List<MemberResponse> members = peerEvaluationService.getMembersForActivity(activityId);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/activities/{activityId}/submission-status")
    public ResponseEntity<Map<String, Object>> getSubmissionStatus(
            @PathVariable Long activityId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long evaluatorId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            evaluatorId = jwtTokenProvider.getUserIdFromToken(authHeader.substring(7));
        }
        return ResponseEntity.ok(peerEvaluationService.getSubmissionStatus(activityId, evaluatorId));
    }

    @PostMapping("/evaluations/submit")
    public ResponseEntity<?> submit(@Valid @RequestBody EvaluationSubmissionRequest request) {
        peerEvaluationService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Evaluation submitted successfully"));
    }
}
