package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.dto.EvaluationResponse;
import group9.advisor_eval_system.entity.Evaluation;
import group9.advisor_eval_system.entity.Questionnaire;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.EvaluationRepository;
import group9.advisor_eval_system.repository.QuestionnaireRepository;
import group9.advisor_eval_system.repository.UserRepository;
import group9.advisor_eval_system.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
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
