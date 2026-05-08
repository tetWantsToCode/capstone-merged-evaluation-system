package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.dto.EvaluationResponse;
import group9.advisor_eval_system.dto.EvaluationScoreDto;
import group9.advisor_eval_system.dto.PendingEvaluationDto;
import group9.advisor_eval_system.dto.StudentEvaluationResponse;
import group9.advisor_eval_system.entity.Evaluation;
import group9.advisor_eval_system.entity.Questionnaire;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.entity.StudentEvaluation;
import group9.advisor_eval_system.repository.EvaluationRepository;
import group9.advisor_eval_system.repository.QuestionnaireRepository;
import group9.advisor_eval_system.repository.StudentEvaluationRepository;
import group9.advisor_eval_system.repository.UserRepository;
import group9.advisor_eval_system.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/teacher/reports")
@RequiredArgsConstructor
public class TeacherReportController {

    private final QuestionnaireRepository questionnaireRepository;
    private final EvaluationRepository evaluationRepository;
    private final StudentEvaluationRepository studentEvaluationRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    private Long getTeacherId(HttpServletRequest request) {
        return jwtUtil.extractUserId(request.getHeader("Authorization").substring(7));
    }

    @GetMapping("/questionnaires")
    public ResponseEntity<?> getTeacherQuestionnaires(HttpServletRequest request) {
        try {
            Long teacherId = getTeacherId(request);
            User teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));

            if (teacher.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only teachers can access reports"));
            }

            List<Questionnaire> questionnaires = questionnaireRepository
                    .findByCreatedByTeacherIdAndIsActiveTrue(teacherId);

            List<Map<String, Object>> response = questionnaires.stream()
                    .map(q -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", q.getId());
                        map.put("title", q.getTitle());
                        map.put("description", q.getDescription() != null ? q.getDescription() : "");
                        map.put("target", q.getTarget() != null ? q.getTarget().toString() : "ADVISER");
                        map.put("createdAt", q.getCreatedAt().toString());
                        return map;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching teacher questionnaires", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/questionnaire/{questionnaireId}/evaluations")
    public ResponseEntity<?> getQuestionnaireEvaluations(
            @PathVariable Long questionnaireId,
            HttpServletRequest request
    ) {
        try {
            Long teacherId = getTeacherId(request);
            User teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));

            if (teacher.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only teachers can access reports"));
            }

            Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                    .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

            if (!questionnaire.getCreatedByTeacher().getId().equals(teacherId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only view reports for your own questionnaires"));
            }

            List<Evaluation> evaluations = evaluationRepository.findByQuestionnaireId(questionnaireId);

            List<EvaluationResponse> response = evaluations.stream()
                    .map(EvaluationResponse::fromEntity)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching questionnaire evaluations", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/questionnaire/{questionnaireId}/student-evaluations")
    public ResponseEntity<?> getStudentQuestionnaireEvaluations(
            @PathVariable Long questionnaireId,
            HttpServletRequest request
    ) {
        try {
            Long teacherId = getTeacherId(request);
            Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                    .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

            if (!questionnaire.getCreatedByTeacher().getId().equals(teacherId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only view reports for your own questionnaires"));
            }

            List<StudentEvaluation> evaluations = studentEvaluationRepository.findByQuestionnaireId(questionnaireId);

            // Group and deduplicate evaluations: For each (evaluator, evaluatee) pairing, 
            // only keep the "best" one (SUBMITTED > IN_PROGRESS, then Latest > Oldest)
            Map<String, Map<String, Object>> consolidated = new HashMap<>();
            
            evaluations.forEach(e -> {
                // Skip adviser-created student evals (student is null, adviser is set)
                if (e.getStudent() == null) return;

                String evaluatorId = e.getStudent().getId().toString();
                boolean isSelf = (e.getEvaluatee() == null) || (e.getEvaluatee().getId().equals(e.getStudent().getId()));
                
                String evaluateeId = e.getEvaluatee() != null ? e.getEvaluatee().getId().toString() : "null";
                String key = isSelf ? (evaluatorId + "_SELF") : (evaluatorId + "_" + evaluateeId);
                
                Map<String, Object> currentMap = new HashMap<>();
                currentMap.put("id", e.getId());
                currentMap.put("evaluatorName", e.getStudent().getFirstName() + " " + e.getStudent().getLastName());
                
                String teamName = "No Team";
                if (e.getStudent().getTeamStudents() != null && !e.getStudent().getTeamStudents().isEmpty()) {
                    teamName = e.getStudent().getTeamStudents().get(0).getTeam().getName();
                }
                currentMap.put("teamName", teamName);

                currentMap.put("isSelf", isSelf);
                
                currentMap.put("evaluateeName", e.getEvaluatee() != null ? e.getEvaluatee().getFirstName() + " " + e.getEvaluatee().getLastName() : "Self");
                currentMap.put("status", e.getStatus());
                
                // For sorting/dedup
                currentMap.put("_statusOrder", e.getStatus() == StudentEvaluation.EvaluationStatus.SUBMITTED ? 0 : 1);
                currentMap.put("_time", e.getSubmittedAt() != null ? e.getSubmittedAt() : (e.getCreatedAt() != null ? e.getCreatedAt() : java.time.LocalDateTime.MIN));
                
                currentMap.put("submittedAt", e.getSubmittedAt());
                currentMap.put("scoreCount", e.getScores() != null ? e.getScores().size() : 0);

                // Calculate Average Score
                if (e.getScores() != null && !e.getScores().isEmpty()) {
                    List<Double> scores = e.getScores().stream()
                            .map(s -> s.getNumericScore())
                            .filter(v -> v != null)
                            .collect(Collectors.toList());
                    if (!scores.isEmpty()) {
                        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                        currentMap.put("averageScore", Math.round(avg * 100.0) / 100.0);
                    } else {
                        currentMap.put("averageScore", null);
                    }
                } else {
                    currentMap.put("averageScore", null);
                }

                if (!consolidated.containsKey(key)) {
                    consolidated.put(key, currentMap);
                } else {
                    Map<String, Object> existing = consolidated.get(key);
                    int currentOrder = (int) currentMap.get("_statusOrder");
                    int existingOrder = (int) existing.get("_statusOrder");
                    
                    if (currentOrder < existingOrder) {
                        // Current is SUBMITTED, existing is not
                        consolidated.put(key, currentMap);
                    } else if (currentOrder == existingOrder) {
                        // Same status, check time
                        java.time.LocalDateTime currentTime = (java.time.LocalDateTime) currentMap.get("_time");
                        java.time.LocalDateTime existingTime = (java.time.LocalDateTime) existing.get("_time");
                        if (currentTime.isAfter(existingTime)) {
                            consolidated.put(key, currentMap);
                        }
                    }
                }
            });

            List<Map<String, Object>> response = new ArrayList<>(consolidated.values());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching student peer evaluations", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/student-evaluation/{evaluationId}")
    public ResponseEntity<?> getStudentEvaluationDetails(
            @PathVariable Long evaluationId,
            HttpServletRequest request
    ) {
        try {
            Long teacherId = getTeacherId(request);
            User teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));

            if (teacher.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only teachers can access reports"));
            }

            StudentEvaluation evaluation = studentEvaluationRepository.findByIdWithDetails(evaluationId)
                    .orElseThrow(() -> new RuntimeException("Student evaluation not found"));

            if (!evaluation.getQuestionnaire().getCreatedByTeacher().getId().equals(teacherId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only view evaluations for your own questionnaires"));
            }

            return ResponseEntity.ok(StudentEvaluationResponse.fromEntity(evaluation));
        } catch (Exception e) {
            log.error("Error fetching student evaluation details", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/evaluation/{evaluationId}")
    public ResponseEntity<?> getEvaluationDetails(
            @PathVariable Long evaluationId,
            HttpServletRequest request
    ) {
        try {
            Long teacherId = getTeacherId(request);
            User teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));

            if (teacher.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only teachers can access reports"));
            }

            Evaluation evaluation = evaluationRepository.findByIdWithFullDetails(evaluationId)
                    .orElseThrow(() -> new RuntimeException("Evaluation not found"));

            if (!evaluation.getQuestionnaire().getCreatedByTeacher().getId().equals(teacherId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only view evaluations for your own questionnaires"));
            }

            EvaluationResponse response = EvaluationResponse.fromEntity(evaluation);

            // Populate per-student scores for individual sections
            if (evaluation.getQuestionnaire() != null
                    && evaluation.getTeam() != null
                    && evaluation.getAdviser() != null) {
                List<StudentEvaluation> individualEvals = studentEvaluationRepository
                        .findByAdviserIdAndTeamIdAndQuestionnaireIdWithScores(
                                evaluation.getAdviser().getId(),
                                evaluation.getTeam().getId(),
                                evaluation.getQuestionnaire().getId());

                List<EvaluationResponse.IndividualStudentScores> indScores = individualEvals.stream()
                        .filter(se -> se.getEvaluatee() != null)
                        .map(se -> {
                            String studentName = se.getEvaluatee().getFirstName() + " " + se.getEvaluatee().getLastName();
                            List<EvaluationScoreDto> scoreDtos = new ArrayList<>(se.getScores()).stream()
                                    .map(EvaluationScoreDto::fromStudentScore)
                                    .filter(s -> s != null)
                                    .collect(Collectors.toList());
                            return new EvaluationResponse.IndividualStudentScores(
                                    se.getEvaluatee().getId(), studentName, scoreDtos);
                        })
                        .collect(Collectors.toList());

                response.setIndividualStudentScores(indScores);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching evaluation details", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pending-evaluations")
    public ResponseEntity<?> getPendingEvaluations(HttpServletRequest request) {
        try {
            Long teacherId = getTeacherId(request);
            User teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));

            if (teacher.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only teachers can access this"));
            }

            // Get all pending evaluations for this teacher's classes
            List<Evaluation> pendingEvaluations = evaluationRepository.findPendingEvaluationsByTeacherId(teacherId);

            // Convert to DTOs
            List<PendingEvaluationDto> response = pendingEvaluations.stream()
                    .map(eval -> new PendingEvaluationDto(
                            eval.getId(),
                            eval.getAdviser().getId(),
                            (eval.getAdviser().getFirstName() != null ? eval.getAdviser().getFirstName() : "") + " " +
                            (eval.getAdviser().getLastName() != null ? eval.getAdviser().getLastName() : ""),
                            eval.getTeam().getSchoolClass().getId(),
                            eval.getTeam().getSchoolClass().getName(),
                            eval.getTeam().getId(),
                            eval.getTeam().getName(),
                            eval.getQuestionnaire().getId(),
                            eval.getQuestionnaire().getTitle()
                    ))
                    .collect(Collectors.toList());

            Map<String, Object> result = Map.of(
                    "total", response.size(),
                    "pending", response
            );

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching pending evaluations", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
