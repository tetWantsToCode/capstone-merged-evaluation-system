package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.entity.*;
import group9.advisor_eval_system.repository.*;
import group9.advisor_eval_system.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/teacher/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final StudentEvaluationRepository studentEvaluationRepository;
    private final TeamRepository teamRepository;
    private final TeamStudentRepository teamStudentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    private Long getTeacherId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return jwtUtil.extractUserId(authHeader.substring(7));
    }

    private boolean isTeacherRole(Long teacherId) {
        return userRepository.findById(teacherId)
                .map(u -> u.getRole() == User.UserRole.TEACHER)
                .orElse(false);
    }

    /**
     * GET /api/teacher/performance/teams
     * Returns all teams (in teacher's classes) that have at least 1 SUBMITTED
     * StudentEvaluation of type ADVISER_STUDENT or STUDENT (peer).
     */
    @GetMapping("/teams")
    public ResponseEntity<?> getTeamsWithCompletedEvaluations(HttpServletRequest request) {
        try {
            Long teacherId = getTeacherId(request);
            if (!isTeacherRole(teacherId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only teachers can access performance data"));
            }

            // Count adviser-student evals per team
            List<Object[]> adviserRows = studentEvaluationRepository
                    .countAdviserStudentEvalsByTeamForTeacher(teacherId);
            Map<Long, Long> adviserCountMap = new HashMap<>();
            adviserRows.forEach(row -> adviserCountMap.put((Long) row[0], (Long) row[1]));

            // Count peer evals per team (via evaluatee's team membership)
            List<Object[]> peerRows = studentEvaluationRepository
                    .countPeerEvalsByTeamForTeacher(teacherId);
            Map<Long, Long> peerCountMap = new HashMap<>();
            peerRows.forEach(row -> peerCountMap.put((Long) row[0], (Long) row[1]));

            // Union of all team IDs that have any type of student eval
            Set<Long> allTeamIds = new HashSet<>();
            allTeamIds.addAll(adviserCountMap.keySet());
            allTeamIds.addAll(peerCountMap.keySet());

            if (allTeamIds.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<Team> teams = teamRepository.findAllById(allTeamIds);

            List<Map<String, Object>> result = teams.stream().map(team -> {
                long adviserCount = adviserCountMap.getOrDefault(team.getId(), 0L);
                long peerCount = peerCountMap.getOrDefault(team.getId(), 0L);
                int memberCount = teamStudentRepository.findByTeamId(team.getId()).size();

                Map<String, Object> entry = new HashMap<>();
                entry.put("id", team.getId());
                entry.put("name", team.getName());
                entry.put("className", team.getSchoolClass() != null ? team.getSchoolClass().getName() : "");
                entry.put("memberCount", memberCount);
                entry.put("adviserEvalCount", adviserCount);
                entry.put("peerEvalCount", peerCount);
                entry.put("completedEvalCount", adviserCount + peerCount);
                return entry;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching performance teams", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/teacher/performance/teams/{teamId}/students
     * Returns all students in the team with their adviser and peer eval counts.
     */
    @GetMapping("/teams/{teamId}/students")
    public ResponseEntity<?> getStudentsInTeam(@PathVariable Long teamId, HttpServletRequest request) {
        try {
            Long teacherId = getTeacherId(request);
            if (!isTeacherRole(teacherId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only teachers can access performance data"));
            }

            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new RuntimeException("Team not found"));

            // Verify the team belongs to this teacher's class
            if (team.getSchoolClass() == null || !teacherId.equals(team.getSchoolClass().getTeacherId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only view teams from your own classes"));
            }

            List<TeamStudent> teamStudents = teamStudentRepository.findByTeamId(teamId);

            List<Map<String, Object>> students = teamStudents.stream().map(ts -> {
                Student s = ts.getStudent();
                long adviserCount = studentEvaluationRepository
                        .countAdviserStudentEvalsByEvaluateeId(s.getId());
                long peerCount = studentEvaluationRepository
                        .countPeerEvalsByEvaluateeId(s.getId());

                Map<String, Object> entry = new HashMap<>();
                entry.put("id", s.getId());
                entry.put("firstName", s.getFirstName());
                entry.put("lastName", s.getLastName());
                entry.put("studentNumber", s.getStudentId());
                entry.put("adviserEvalCount", adviserCount);
                entry.put("peerEvalCount", peerCount);
                return entry;
            }).collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("teamId", teamId);
            result.put("teamName", team.getName());
            result.put("students", students);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching team students for performance", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/teacher/performance/students/{studentId}/individual
     * Returns SUBMITTED adviser-student (ADVISER_STUDENT type) evaluations received
     * by this student, with full Q&A scores and questionnaire metadata.
     */
    @GetMapping("/students/{studentId}/individual")
    public ResponseEntity<?> getIndividualPerformance(@PathVariable Long studentId, HttpServletRequest request) {
        try {
            Long teacherId = getTeacherId(request);
            if (!isTeacherRole(teacherId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only teachers can access performance data"));
            }

            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            List<StudentEvaluation> evals = studentEvaluationRepository
                    .findAdviserStudentByEvaluateeWithDetails(studentId);

            List<Map<String, Object>> evaluations = evals.stream()
                    .map(this::buildAdviserStudentEvalMap)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("studentId", studentId);
            result.put("studentName", student.getFirstName() + " " + student.getLastName());
            result.put("evaluations", evaluations);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching individual performance for student {}", studentId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/teacher/performance/students/{studentId}/peer
     * Returns SUBMITTED peer (STUDENT type) evaluations received by this student,
     * with full Q&A scores and questionnaire metadata. Self-evaluations are excluded.
     */
    @GetMapping("/students/{studentId}/peer")
    public ResponseEntity<?> getPeerPerformance(@PathVariable Long studentId, HttpServletRequest request) {
        try {
            Long teacherId = getTeacherId(request);
            if (!isTeacherRole(teacherId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only teachers can access performance data"));
            }

            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            List<StudentEvaluation> peerEvals = studentEvaluationRepository
                    .findByEvaluateeIdAndStatusWithDetails(studentId, StudentEvaluation.EvaluationStatus.SUBMITTED)
                    .stream()
                    .filter(e -> e.getStudent() != null && !e.getStudent().getId().equals(studentId))
                    .collect(Collectors.toList());

            List<Map<String, Object>> evaluations = peerEvals.stream()
                    .map(this::buildPeerEvalMap)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("studentId", studentId);
            result.put("studentName", student.getFirstName() + " " + student.getLastName());
            result.put("evaluations", evaluations);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching peer performance for student {}", studentId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds the response map for a single ADVISER_STUDENT evaluation
     * (where adviser evaluates an individual student).
     */
    private Map<String, Object> buildAdviserStudentEvalMap(StudentEvaluation eval) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", eval.getId());

        String adviserName = "";
        if (eval.getAdviser() != null) {
            adviserName = ((eval.getAdviser().getFirstName() != null ? eval.getAdviser().getFirstName() : "") +
                    " " + (eval.getAdviser().getLastName() != null ? eval.getAdviser().getLastName() : "")).trim();
        }
        map.put("adviserName", adviserName);

        if (eval.getTeam() != null) {
            map.put("teamId", eval.getTeam().getId());
            map.put("teamName", eval.getTeam().getName());
        } else {
            map.put("teamId", null);
            map.put("teamName", "");
        }

        map.put("generalComments", null); // StudentEvaluation has no generalComments field
        map.put("status", eval.getStatus().name());
        map.put("submittedAt", eval.getSubmittedAt());

        Questionnaire q = eval.getQuestionnaire();
        if (q != null) {
            map.put("questionnaireId", q.getId());
            map.put("questionnaireTitle", q.getTitle());
        } else {
            map.put("questionnaireId", null);
            map.put("questionnaireTitle", "N/A");
        }

        List<Map<String, Object>> scoreMaps = buildStudentScoreMaps(eval);
        map.put("scores", scoreMaps);
        map.put("scoresSummary", computeScoreSummary(scoreMaps));

        return map;
    }

    /**
     * Builds the response map for a single STUDENT peer evaluation.
     */
    private Map<String, Object> buildPeerEvalMap(StudentEvaluation eval) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", eval.getId());

        String evaluatorName = "";
        if (eval.getStudent() != null) {
            evaluatorName = ((eval.getStudent().getFirstName() != null ? eval.getStudent().getFirstName() : "") +
                    " " + (eval.getStudent().getLastName() != null ? eval.getStudent().getLastName() : "")).trim();
        }
        map.put("evaluatorName", evaluatorName);
        map.put("status", eval.getStatus().name());
        map.put("submittedAt", eval.getSubmittedAt());

        Questionnaire q = eval.getQuestionnaire();
        if (q != null) {
            map.put("questionnaireId", q.getId());
            map.put("questionnaireTitle", q.getTitle());
        } else {
            map.put("questionnaireId", null);
            map.put("questionnaireTitle", "N/A");
        }

        List<Map<String, Object>> scoreMaps = buildStudentScoreMaps(eval);
        map.put("scores", scoreMaps);
        map.put("scoresSummary", computeScoreSummary(scoreMaps));

        return map;
    }

    private List<Map<String, Object>> buildStudentScoreMaps(StudentEvaluation eval) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (eval.getScores() == null) return result;

        Map<Long, QuestionnaireItem> itemById = new HashMap<>();
        Map<Long, String> itemSectionTitle = new HashMap<>();
        if (eval.getQuestionnaire() != null) {
            if (eval.getQuestionnaire().getItems() != null) {
                eval.getQuestionnaire().getItems().forEach(item -> itemById.put(item.getId(), item));
            }
            if (eval.getQuestionnaire().getSections() != null) {
                eval.getQuestionnaire().getSections().forEach(section -> {
                    if (section.getItems() != null) {
                        section.getItems().forEach(item -> {
                            itemById.put(item.getId(), item);
                            itemSectionTitle.put(item.getId(), section.getSectionTitle());
                        });
                    }
                });
            }
        }

        eval.getScores().forEach(score -> {
            Map<String, Object> s = new HashMap<>();
            s.put("id", score.getId());
            s.put("numericScore", score.getNumericScore());
            s.put("textResponse", score.getTextResponse());
            Long itemId = score.getQuestionnaireItem() != null ? score.getQuestionnaireItem().getId() : null;
            s.put("questionnaireItemId", itemId);

            if (itemId != null && itemById.containsKey(itemId)) {
                QuestionnaireItem item = itemById.get(itemId);
                s.put("questionText", item.getQuestionText());
                s.put("minScore", item.getMinScore());
                s.put("maxScore", item.getMaxScore());
                s.put("sectionTitle", itemSectionTitle.getOrDefault(itemId, null));
            } else {
                s.put("questionText", "Question");
                s.put("minScore", null);
                s.put("maxScore", null);
                s.put("sectionTitle", null);
            }
            result.add(s);
        });
        return result;
    }

    private Map<String, Object> computeScoreSummary(List<Map<String, Object>> scores) {
        double total = 0;
        double maxPossible = 0;
        int numericCount = 0;

        for (Map<String, Object> s : scores) {
            Object num = s.get("numericScore");
            Object max = s.get("maxScore");
            if (num != null) {
                total += ((Number) num).doubleValue();
                numericCount++;
                if (max != null) {
                    maxPossible += ((Number) max).doubleValue();
                }
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalScore", numericCount > 0 ? Math.round(total * 100.0) / 100.0 : null);
        summary.put("maxPossible", maxPossible > 0 ? Math.round(maxPossible * 100.0) / 100.0 : null);
        summary.put("percentage", (numericCount > 0 && maxPossible > 0)
                ? Math.round((total / maxPossible) * 10000.0) / 100.0
                : null);
        summary.put("numericCount", numericCount);
        return summary;
    }
}

