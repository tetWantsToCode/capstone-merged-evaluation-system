package group9.advisor_eval_system.apeer.controller;

import group9.advisor_eval_system.apeer.dto.ActivityResponse;
import group9.advisor_eval_system.apeer.dto.CreateActivityRequest;
import group9.advisor_eval_system.apeer.entity.PeerEvaluation;
import group9.advisor_eval_system.apeer.repository.PeerEvaluationRepository;
import group9.advisor_eval_system.apeer.service.EvaluationActivityService;
import group9.advisor_eval_system.apeer.service.PeerFeedbackSummaryService;
import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.repository.StudentRepository;
import group9.advisor_eval_system.util.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * APEER: Create and list evaluation activities. SRS 5.1.
 * Base path /api/apeer to avoid affecting existing endpoints.
 */
@RestController
@RequestMapping("/api/apeer/activities")
@RequiredArgsConstructor
public class ApeerActivityController {

    private final EvaluationActivityService activityService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PeerFeedbackSummaryService summaryService;
    private final PeerEvaluationRepository peerEvaluationRepository;
    private final StudentRepository studentRepository;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateActivityRequest request,
                                    @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long teacherId = resolveTeacherId(authHeader);
        ActivityResponse created = activityService.create(request, teacherId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ActivityResponse>> list(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long teacherId = resolveTeacherId(authHeader);
        return ResponseEntity.ok(activityService.listByTeacher(teacherId));
    }

    @GetMapping("/for-student")
    public ResponseEntity<List<ActivityResponse>> listForStudent(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String email = resolveUserEmail(authHeader);
        return ResponseEntity.ok(activityService.listForStudent(email));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return ResponseEntity.ok(activityService.getById(id));
    }

    private Long resolveTeacherId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Unauthorized: missing or invalid token");
        }
        String token = authHeader.substring(7);
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        if (userId == null) {
            throw new RuntimeException("Unauthorized: invalid token");
        }
        return userId;
    }

    private String resolveUserEmail(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Unauthorized: missing or invalid token");
        }
        String token = authHeader.substring(7);
        return jwtTokenProvider.getEmailFromToken(token);
    }

    /**
     * GET /api/apeer/student/ai-summary
     * Returns AI-generated strengths/weaknesses/suggestions summary for the authenticated student.
     * SRS REQ-601 to REQ-607.
     */
    @GetMapping("/student/ai-summary")
    public ResponseEntity<?> getAiSummary(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String email = resolveUserEmail(authHeader);
        Map<String, Object> summary = summaryService.generateSummaryForStudent(email);
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/apeer/student/history
     * Returns all peer evaluations where the authenticated student is the target.
     * SRS REQ-701: student can view their own evaluation history.
     */
    @GetMapping("/student/history")
    public ResponseEntity<?> getStudentHistory(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String email = resolveUserEmail(authHeader);
        Student student = studentRepository.findByEmail(email != null ? email.toLowerCase().trim() : "")
                .orElse(null);
        if (student == null) {
            return ResponseEntity.ok(List.of());
        }
        List<PeerEvaluation> evaluations = peerEvaluationRepository.findByTargetStudentId(student.getId());
        List<Map<String, Object>> result = evaluations.stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("activityId", e.getActivity() != null ? e.getActivity().getId() : null);
            item.put("activityTitle", e.getActivity() != null ? e.getActivity().getTitle() : null);
            item.put("rubricScores", e.getRubricScores());
            int total = e.getRubricScores() != null
                    ? e.getRubricScores().values().stream().mapToInt(Integer::intValue).sum() : 0;
            item.put("totalScore", total);
            item.put("submittedAt", e.getSubmittedAt() != null ? e.getSubmittedAt().toString() : null);
            return item;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        if (e.getMessage() != null && e.getMessage().startsWith("Unauthorized")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Bad request"));
    }
}
