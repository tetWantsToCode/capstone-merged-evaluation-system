package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.entity.*;
import group9.advisor_eval_system.dto.QuestionnaireWithItemsDto;
import group9.advisor_eval_system.repository.UserRepository;
import group9.advisor_eval_system.service.StudentEvaluationService;
import group9.advisor_eval_system.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentDashboardController {

    private final StudentEvaluationService studentEvaluationService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    private User getAuthenticatedUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        Long userId = jwtUtil.extractUserId(token);
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Student getAuthenticatedStudent(HttpServletRequest request) {
        User user = getAuthenticatedUser(request);
        if (user.getRole() != User.UserRole.STUDENT) {
            throw new RuntimeException("Only students can access this endpoint");
        }
        return studentEvaluationService.getStudentByEmail(user.getEmail());
    }

    @GetMapping("/team")
    public ResponseEntity<?> getMyTeam(HttpServletRequest request) {
        try {
            Student student = getAuthenticatedStudent(request);
            List<TeamStudent> teamStudents = student.getTeamStudents();
            
            if (teamStudents == null || teamStudents.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "You are not currently assigned to any team."));
            }
            
            // Get the first active team
            Team team = teamStudents.get(0).getTeam();
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", team.getId());
            response.put("name", team.getName());
            response.put("description", team.getDescription());
            
            List<Map<String, Object>> members = team.getTeamStudents().stream().map(ts -> {
                Student member = ts.getStudent();
                Map<String, Object> map = new HashMap<>();
                map.put("id", member.getId());
                map.put("name", member.getFirstName() + " " + member.getLastName());
                map.put("email", member.getEmail());
                map.put("position", ts.getPosition());
                map.put("isMe", member.getId().equals(student.getId()));
                return map;
            }).collect(Collectors.toList());
            
            response.put("members", members);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching team", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/questionnaires")
    public ResponseEntity<?> getAssignedQuestionnaires(HttpServletRequest request) {
        try {
            Student student = getAuthenticatedStudent(request);
            List<Questionnaire> questionnaires = studentEvaluationService.getAssignedQuestionnaires(student.getId());
            
            List<TeamStudent> teamStudents = student.getTeamStudents();
            List<Student> teammates = (teamStudents != null && !teamStudents.isEmpty()) 
                ? teamStudents.get(0).getTeam().getMembers() 
                : Collections.singletonList(student);

            List<Map<String, Object>> response = questionnaires.stream().map(q -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", q.getId());
                map.put("title", q.getTitle());
                map.put("description", q.getDescription());
                map.put("createdAt", q.getCreatedAt());
                
                // Return status for each teammate (peer evaluation)
                List<Map<String, Object>> peerTasks = teammates.stream().map(peer -> {
                    Map<String, Object> peerMap = new HashMap<>();
                    peerMap.put("peerId", peer.getId());
                    peerMap.put("peerName", peer.getFirstName() + " " + peer.getLastName() + (peer.getId().equals(student.getId()) ? " (Self)" : ""));
                    
                    try {
                        StudentEvaluation eval = studentEvaluationService.getOrCreateStudentEvaluation(student.getId(), q.getId(), peer.getId());
                        peerMap.put("status", eval.getStatus());
                        peerMap.put("evaluationId", eval.getId());
                    } catch (Exception e) {
                        peerMap.put("status", "READY");
                    }
                    return peerMap;
                }).collect(Collectors.toList());
                
                map.put("peerTasks", peerTasks);
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching assigned questionnaires", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/evaluations/{questionnaireId}")
    public ResponseEntity<?> getEvaluation(
            @PathVariable Long questionnaireId, 
            @RequestParam(required = false) Long peerId,
            HttpServletRequest request) {
        try {
            Student student = getAuthenticatedStudent(request);
            StudentEvaluation evaluation = studentEvaluationService.getOrCreateStudentEvaluation(student.getId(), questionnaireId, peerId);

            Map<String, Object> response = new HashMap<>();
            response.put("evaluationId", evaluation.getId());
            response.put("status", evaluation.getStatus());
            response.put("questionnaire", QuestionnaireWithItemsDto.fromEntity(evaluation.getQuestionnaire()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching evaluation", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/evaluations/group/{questionnaireId}")
    public ResponseEntity<?> getGroupEvaluation(
            @PathVariable Long questionnaireId,
            HttpServletRequest request) {
        try {
            Student student = getAuthenticatedStudent(request);
            List<TeamStudent> teamStudents = student.getTeamStudents();
            if (teamStudents == null || teamStudents.isEmpty()) {
                throw new RuntimeException("You are not part of a team");
            }

            Team team = teamStudents.get(0).getTeam();
            List<Student> members = team.getTeamStudents().stream()
                    .map(TeamStudent::getStudent)
                    .collect(Collectors.toList());

            List<Map<String, Object>> memberDetails = new ArrayList<>();
            Map<Long, Map<Long, Object>> allAnswers = new HashMap<>();
            String status = "SUBMITTED"; // Starts as submitted, set to IN_PROGRESS if any is in progress

            Questionnaire q = null;
            for (Student member : members) {
                StudentEvaluation eval = studentEvaluationService.getOrCreateStudentEvaluation(student.getId(), questionnaireId, member.getId());
                if (q == null) q = eval.getQuestionnaire();
                
                Map<String, Object> mDetails = new HashMap<>();
                mDetails.put("id", member.getId());
                mDetails.put("name", member.getFirstName() + " " + member.getLastName());
                mDetails.put("isMe", member.getId().equals(student.getId()));
                mDetails.put("evaluationId", eval.getId());
                memberDetails.add(mDetails);

                if (eval.getStatus() == StudentEvaluation.EvaluationStatus.IN_PROGRESS) {
                    status = "IN_PROGRESS";
                }

                Map<Long, Object> answers = new HashMap<>();
                if (eval.getScores() != null) {
                    for (StudentEvaluationScore score : eval.getScores()) {
                        if (score.getQuestionnaireItem() != null) {
                            if (score.getTextResponse() != null) {
                                answers.put(score.getQuestionnaireItem().getId(), score.getTextResponse());
                            } else if (score.getNumericScore() != null) {
                                answers.put(score.getQuestionnaireItem().getId(), score.getNumericScore());
                            }
                        }
                    }
                }
                allAnswers.put(eval.getId(), answers);
            }

            if (q == null) {
                throw new RuntimeException("Questionnaire not found or no members to evaluate");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("questionnaire", QuestionnaireWithItemsDto.fromEntity(q));
            response.put("members", memberDetails);
            response.put("answers", allAnswers);
            response.put("status", status);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching group evaluation", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/evaluations/group/save")
    public ResponseEntity<?> saveGroupEvaluation(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        try {
            Student student = getAuthenticatedStudent(request);
            Map<String, Map<String, Object>> answersRaw = (Map<String, Map<String, Object>>) payload.get("answers");
            
            Map<Long, Map<Long, Object>> groupAnswers = new HashMap<>();
            if (answersRaw != null) {
                for (Map.Entry<String, Map<String, Object>> entry : answersRaw.entrySet()) {
                    Long evalId = Long.valueOf(entry.getKey());
                    Map<Long, Object> answers = new HashMap<>();
                    for (Map.Entry<String, Object> innerEntry : entry.getValue().entrySet()) {
                        answers.put(Long.valueOf(innerEntry.getKey()), innerEntry.getValue());
                    }
                    groupAnswers.put(evalId, answers);
                }
            }

            studentEvaluationService.saveGroupEvaluation(student.getId(), groupAnswers);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error saving group evaluation", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/evaluations/group/submit")
    public ResponseEntity<?> submitGroupEvaluations(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        try {
            Student student = getAuthenticatedStudent(request);
            List<Object> evalIdsRaw = (List<Object>) payload.get("evaluationIds");
            List<Long> evaluationIds = evalIdsRaw.stream()
                    .map(o -> Long.valueOf(o.toString()))
                    .collect(Collectors.toList());

            studentEvaluationService.submitGroupEvaluations(student.getId(), evaluationIds);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error submitting group evaluation", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/evaluations/save")
    public ResponseEntity<?> saveEvaluation(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        try {
            Student student = getAuthenticatedStudent(request);
            Long evaluationId = Long.valueOf(payload.get("evaluationId").toString());
            
            Map<String, Object> answersRaw = (Map<String, Object>) payload.get("answers");
            Map<Long, Object> answers = new HashMap<>();
            if (answersRaw != null) {
                for (Map.Entry<String, Object> entry : answersRaw.entrySet()) {
                    answers.put(Long.valueOf(entry.getKey()), entry.getValue());
                }
            }

            StudentEvaluation evaluation = studentEvaluationService.saveEvaluation(student.getId(), evaluationId, answers);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "status", evaluation.getStatus(),
                "evaluationId", evaluation.getId()
            ));
        } catch (Exception e) {
            log.error("Error saving evaluation", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/evaluations/submit/{evaluationId}")
    public ResponseEntity<?> submitEvaluation(@PathVariable Long evaluationId, HttpServletRequest request) {
        try {
            Student student = getAuthenticatedStudent(request);
            StudentEvaluation evaluation = studentEvaluationService.submitEvaluation(student.getId(), evaluationId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "status", evaluation.getStatus(),
                "submittedAt", evaluation.getSubmittedAt()
            ));
        } catch (Exception e) {
            log.error("Error submitting evaluation", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<?> getReportSummary(HttpServletRequest request) {
        try {
            Student student = getAuthenticatedStudent(request);
            return ResponseEntity.ok(studentEvaluationService.getStudentReportSummary(student.getId()));
        } catch (Exception e) {
            log.error("Error fetching student report summary", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}
