package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.dto.EvaluationResponse;
import group9.advisor_eval_system.entity.Evaluation;
import group9.advisor_eval_system.entity.EvaluationScore;
import group9.advisor_eval_system.entity.Questionnaire;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.EvaluationRepository;
import group9.advisor_eval_system.repository.EvaluationScoreRepository;
import group9.advisor_eval_system.repository.QuestionnaireRepository;
import group9.advisor_eval_system.repository.UserRepository;
import group9.advisor_eval_system.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/teacher/reports")
@RequiredArgsConstructor
public class TeacherReportController {

    private final QuestionnaireRepository questionnaireRepository;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationScoreRepository evaluationScoreRepository;
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
                    .map(q -> Map.of(
                            "id", (Object) q.getId(),
                            "title", q.getTitle(),
                            "description", q.getDescription() != null ? q.getDescription() : "",
                            "createdAt", q.getCreatedAt().toString()
                    ))
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

    /**
     * GET /api/teacher/reports/questionnaire/{id}/rankings
     * Computes and returns team performance rankings for a questionnaire. REQ-301, REQ-302.
     */
    @GetMapping("/questionnaire/{questionnaireId}/rankings")
    public ResponseEntity<?> getTeamRankings(
            @PathVariable Long questionnaireId,
            HttpServletRequest request
    ) {
        try {
            Long teacherId = getTeacherId(request);
            User teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));
            if (teacher.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
            }
            Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                    .orElseThrow(() -> new RuntimeException("Questionnaire not found"));
            if (!questionnaire.getCreatedByTeacher().getId().equals(teacherId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
            }

            List<Evaluation> evaluations = evaluationRepository.findByQuestionnaireId(questionnaireId).stream()
                    .filter(e -> e.getStatus() == Evaluation.EvaluationStatus.SUBMITTED)
                    .collect(Collectors.toList());

            List<Map<String, Object>> rankings = new ArrayList<>();
            for (Evaluation eval : evaluations) {
                List<EvaluationScore> scores = evaluationScoreRepository.findByEvaluationId(eval.getId());
                double totalScore = scores.stream()
                        .mapToDouble(s -> s.getNumericScore() != null ? s.getNumericScore() : 0.0)
                        .sum();
                double avgScore = scores.isEmpty() ? 0.0 : totalScore / scores.size();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("teamId", eval.getTeam() != null ? eval.getTeam().getId() : null);
                row.put("teamName", eval.getTeam() != null ? eval.getTeam().getName() : "Unknown");
                row.put("adviserName", eval.getAdviser() != null
                        ? (eval.getAdviser().getFirstName() + " " + eval.getAdviser().getLastName()).trim()
                        : "Unknown");
                row.put("totalScore", Math.round(totalScore * 100.0) / 100.0);
                row.put("averageScore", Math.round(avgScore * 100.0) / 100.0);
                row.put("itemCount", scores.size());
                row.put("submittedAt", eval.getSubmittedAt() != null ? eval.getSubmittedAt().toString() : null);
                rankings.add(row);
            }

            rankings.sort(Comparator.comparingDouble(r -> -((Double) r.get("totalScore"))));
            for (int i = 0; i < rankings.size(); i++) {
                rankings.get(i).put("rank", i + 1);
            }
            return ResponseEntity.ok(rankings);
        } catch (Exception e) {
            log.error("Error computing rankings", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/teacher/reports/questionnaire/{id}/export-csv
     * Downloads a CSV file of team evaluation rankings. REQ-306.
     */
    @GetMapping("/questionnaire/{questionnaireId}/export-csv")
    public ResponseEntity<?> exportCsv(
            @PathVariable Long questionnaireId,
            HttpServletRequest request
    ) {
        try {
            Long teacherId = getTeacherId(request);
            User teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Teacher not found"));
            if (teacher.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
            }
            Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                    .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

            List<Evaluation> evaluations = evaluationRepository.findByQuestionnaireId(questionnaireId).stream()
                    .filter(e -> e.getStatus() == Evaluation.EvaluationStatus.SUBMITTED)
                    .sorted(Comparator.comparingDouble(e -> {
                        List<EvaluationScore> s = evaluationScoreRepository.findByEvaluationId(e.getId());
                        return -s.stream().mapToDouble(sc -> sc.getNumericScore() != null ? sc.getNumericScore() : 0.0).sum();
                    }))
                    .collect(Collectors.toList());

            StringBuilder csv = new StringBuilder();
            csv.append("Rank,Team Name,Adviser,Total Score,Average Score,Submitted At\n");
            int rank = 1;
            for (Evaluation eval : evaluations) {
                List<EvaluationScore> scores = evaluationScoreRepository.findByEvaluationId(eval.getId());
                double total = scores.stream().mapToDouble(s -> s.getNumericScore() != null ? s.getNumericScore() : 0.0).sum();
                double avg = scores.isEmpty() ? 0.0 : total / scores.size();
                String team = eval.getTeam() != null ? eval.getTeam().getName() : "Unknown";
                String adviser = eval.getAdviser() != null
                        ? (eval.getAdviser().getFirstName() + " " + eval.getAdviser().getLastName()).trim()
                        : "Unknown";
                String submitted = eval.getSubmittedAt() != null ? eval.getSubmittedAt().toString() : "";
                csv.append(rank++).append(",")
                        .append("\"").append(team.replace("\"", "\"\"")).append("\"").append(",")
                        .append("\"").append(adviser.replace("\"", "\"\"")).append("\"").append(",")
                        .append(Math.round(total * 100.0) / 100.0).append(",")
                        .append(Math.round(avg * 100.0) / 100.0).append(",")
                        .append(submitted).append("\n");
            }

            String filename = "rankings-" + questionnaire.getTitle().replaceAll("[^a-zA-Z0-9]", "_") + ".csv";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv.toString());
        } catch (Exception e) {
            log.error("Error exporting CSV", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
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

            Evaluation evaluation = evaluationRepository.findById(evaluationId)
                    .orElseThrow(() -> new RuntimeException("Evaluation not found"));

            if (!evaluation.getQuestionnaire().getCreatedByTeacher().getId().equals(teacherId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only view evaluations for your own questionnaires"));
            }

            EvaluationResponse response = EvaluationResponse.fromEntity(evaluation);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching evaluation details", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
