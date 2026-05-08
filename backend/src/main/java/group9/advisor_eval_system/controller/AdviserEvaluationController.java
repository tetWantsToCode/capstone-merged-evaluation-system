package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.dto.EvaluationResponse;
import group9.advisor_eval_system.dto.EvaluationScoreDto;
import group9.advisor_eval_system.dto.QuestionnaireWithItemsDto;
import group9.advisor_eval_system.entity.*;
import group9.advisor_eval_system.repository.EvaluationRepository;
import group9.advisor_eval_system.repository.EvaluationScoreRepository;
import group9.advisor_eval_system.repository.StudentEvaluationRepository;
import group9.advisor_eval_system.repository.QuestionnaireRepository;
import group9.advisor_eval_system.service.EvaluationService;
import group9.advisor_eval_system.service.QuestionnaireService;
import group9.advisor_eval_system.service.TeamService;
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
@RequestMapping("/api/adviser")
@RequiredArgsConstructor
public class AdviserEvaluationController {

    private final EvaluationService evaluationService;
    private final TeamService teamService;
    private final QuestionnaireService questionnaireService;
    private final JwtUtil jwtUtil;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationScoreRepository evaluationScoreRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final StudentEvaluationRepository studentEvaluationRepository;

    private Long getAdviserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        return jwtUtil.extractUserId(token);
    }

    private String getErrorMessage(Exception e) {
        if (e.getMessage() != null && !e.getMessage().isEmpty()) {
            return e.getMessage();
        }
        if (e.getCause() != null && e.getCause().getMessage() != null) {
            return e.getCause().getMessage();
        }
        return e.getClass().getSimpleName() + " occurred";
    }

    // ─────────────────────────────────────────────────────────────
    // Existing team-level evaluation endpoints (unchanged)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/teams")
    public ResponseEntity<?> getMyTeams(HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);
            List<Team> teams = teamService.getAllTeams().stream()
                    .filter(t -> new ArrayList<>(t.getAdvisers()).stream().anyMatch(a -> a.getId().equals(adviserId)))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(teams);
        } catch (Exception e) {
            log.error("Error fetching adviser teams: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }

    @GetMapping("/teams/{teamId}/questionnaires")
    public ResponseEntity<?> getTeamQuestionnaires(
            @PathVariable Long teamId,
            HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);
            Team team = teamService.getTeamById(teamId);

            if (new ArrayList<>(team.getAdvisers()).stream().noneMatch(a -> a.getId().equals(adviserId))) {
                throw new RuntimeException("Unauthorized: Adviser not assigned to this team");
            }

            List<Questionnaire> questionnaires = questionnaireService
                    .getQuestionnairesByClass(team.getSchoolClass().getId());
            return ResponseEntity.ok(questionnaires);
        } catch (Exception e) {
            log.error("Error fetching team questionnaires: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }

    @GetMapping("/teams/{teamId}/evaluation-statuses")
    public ResponseEntity<?> getTeamEvaluationStatuses(
            @PathVariable Long teamId,
            HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);
            Team team = teamService.getTeamById(teamId);

            if (new ArrayList<>(team.getAdvisers()).stream().noneMatch(a -> a.getId().equals(adviserId))) {
                throw new RuntimeException("Unauthorized: Adviser not assigned to this team");
            }

            List<Evaluation> evaluations = evaluationRepository.findByAdviserIdAndTeamIdWithProgress(adviserId, teamId);

            List<Map<String, Object>> statuses = evaluations.stream().map(evaluation -> {
                int totalQuestions = 0;
                if (evaluation.getQuestionnaire() != null) {
                    // Count top-level items
                    int topLevel = evaluation.getQuestionnaire().getItems() != null
                            ? new HashSet<>(evaluation.getQuestionnaire().getItems()).size() : 0;
                    // Count items nested inside sections
                    int sectionLevel = 0;
                    if (evaluation.getQuestionnaire().getSections() != null) {
                        for (var section : evaluation.getQuestionnaire().getSections()) {
                            if (section.getItems() != null) {
                                sectionLevel += section.getItems().size();
                            }
                        }
                    }
                    totalQuestions = topLevel + sectionLevel;
                }

                boolean isCompleted = evaluation.getStatus() == Evaluation.EvaluationStatus.SUBMITTED
                        || evaluation.getStatus() == Evaluation.EvaluationStatus.REVIEWED;

                int answeredCount = isCompleted
                        ? totalQuestions
                        : (evaluation.getScores() != null ? evaluation.getScores().size() : 0);
                int progressPercent = isCompleted
                        ? 100
                        : (totalQuestions > 0
                                ? (int) Math.round((answeredCount * 100.0) / totalQuestions)
                                : 0);

                String queueStatus;
                if (isCompleted) {
                    queueStatus = "SUBMITTED";
                } else if (evaluation.getQuestionnaire() != null
                        && !Boolean.TRUE.equals(evaluation.getQuestionnaire().getIsActive())) {
                    queueStatus = "LOCKED";
                } else if (answeredCount > 0) {
                    queueStatus = "IN_PROGRESS";
                } else {
                    queueStatus = "READY";
                }

                Map<String, Object> statusMap = new HashMap<>();
                statusMap.put("questionnaireId",
                        evaluation.getQuestionnaire() != null ? evaluation.getQuestionnaire().getId() : null);
                statusMap.put("evaluationId", evaluation.getId());
                statusMap.put("status", queueStatus);
                statusMap.put("answeredCount", answeredCount);
                statusMap.put("totalQuestions", totalQuestions);
                statusMap.put("progressPercent", progressPercent);
                statusMap.put("updatedAt", evaluation.getUpdatedAt());
                statusMap.put("submittedAt", evaluation.getSubmittedAt());
                return statusMap;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(statuses);
        } catch (Exception e) {
            log.error("Error fetching evaluation statuses for team {}: {}", teamId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }

    @GetMapping("/evaluation/{teamId}/{questionnaireId}")
    public ResponseEntity<?> getOrCreateEvaluation(
            @PathVariable Long teamId,
            @PathVariable Long questionnaireId,
            HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);
            log.info("Getting or creating evaluation for adviser: {}, team: {}, questionnaire: {}", adviserId, teamId,
                    questionnaireId);

            Evaluation evaluation = evaluationService.getOrCreateEvaluation(adviserId, teamId, questionnaireId);

            if (evaluation == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Failed to create or retrieve evaluation"));
            }
            if (evaluation.getAdviser() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Adviser data is missing from evaluation"));
            }
            if (evaluation.getTeam() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Team data is missing from evaluation"));
            }
            if (evaluation.getQuestionnaire() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Questionnaire data is missing from evaluation"));
            }

            EvaluationResponse response = new EvaluationResponse();
            response.setId(evaluation.getId());
            response.setTeamId(evaluation.getTeam().getId());
            response.setTeamName(evaluation.getTeam().getName());
            response.setAdviserId(evaluation.getAdviser().getId());

            String adviserName = (evaluation.getAdviser().getFirstName() != null
                    ? evaluation.getAdviser().getFirstName() : "")
                    + " "
                    + (evaluation.getAdviser().getLastName() != null ? evaluation.getAdviser().getLastName() : "");
            response.setAdviserName(adviserName.trim());
            response.setStatus(evaluation.getStatus() != null ? evaluation.getStatus().name() : "UNKNOWN");
            response.setAllowEdit(evaluation.getAllowEdit() != null ? evaluation.getAllowEdit() : false);
            response.setGeneralComments(evaluation.getGeneralComments());
            response.setSubmittedAt(evaluation.getSubmittedAt());
            response.setCreatedAt(evaluation.getCreatedAt());
            response.setUpdatedAt(evaluation.getUpdatedAt());

            Questionnaire q = questionnaireRepository
                    .findByIdWithSectionsAndItems(evaluation.getQuestionnaire().getId())
                    .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

            QuestionnaireWithItemsDto qDto = QuestionnaireWithItemsDto.fromEntity(q);
            response.setQuestionnaire(qDto);

            List<EvaluationScore> scores = evaluationScoreRepository.findByEvaluationId(evaluation.getId());
            response.setScores(
                    new ArrayList<>(scores).stream().map(EvaluationScoreDto::fromEntity).collect(Collectors.toList()));

            // Populate team with students for mixed questionnaires
            if (evaluation.getTeam() != null) {
                EvaluationResponse.TeamInfo teamInfo = new EvaluationResponse.TeamInfo();
                teamInfo.setId(evaluation.getTeam().getId());
                teamInfo.setName(evaluation.getTeam().getName());
                
                if (evaluation.getTeam().getTeamStudents() != null && !evaluation.getTeam().getTeamStudents().isEmpty()) {
                    teamInfo.setTeamStudents(
                        evaluation.getTeam().getTeamStudents().stream()
                            .filter(ts -> ts != null && ts.getStudent() != null)
                            .map(ts -> new EvaluationResponse.TeamStudentInfo(
                                ts.getStudent().getId(),
                                ts.getStudent().getFirstName(),
                                ts.getStudent().getLastName(),
                                ts.getStudent().getStudentId()
                            ))
                            .collect(Collectors.toList())
                    );
                } else {
                    teamInfo.setTeamStudents(new ArrayList<>());
                }
                response.setTeam(teamInfo);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting evaluation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }

    @PostMapping("/evaluation/save")
    public EvaluationResponse saveEvaluation(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        Long adviserId = getAdviserId(request);
        Long evaluationId = Long.valueOf(payload.get("evaluationId").toString());
        String generalComments = (String) payload.get("generalComments");

        Object answersObj = payload.get("answers");
        if (answersObj == null) {
            throw new RuntimeException("No answers provided in request");
        }
        if (!(answersObj instanceof Map)) {
            throw new RuntimeException("Answers must be a JSON object, got: " + answersObj.getClass().getSimpleName());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> answersRaw = (Map<String, Object>) answersObj;
        Map<Long, Object> answers = new HashMap<>();
        for (Map.Entry<String, Object> entry : answersRaw.entrySet()) {
            try {
                Long itemId = Long.valueOf(entry.getKey());
                answers.put(itemId, entry.getValue());
            } catch (NumberFormatException e) {
                log.error("Invalid item ID in answers: {}", entry.getKey(), e);
                throw new RuntimeException("Invalid question ID: " + entry.getKey());
            }
        }

        Evaluation evaluation = evaluationService.saveEvaluation(adviserId, evaluationId, answers, generalComments);
        return EvaluationResponse.fromEntity(evaluation);
    }

    @PostMapping("/evaluation/submit/{evaluationId}")
    public EvaluationResponse submitEvaluation(
            @PathVariable Long evaluationId,
            HttpServletRequest request) {
        Long adviserId = getAdviserId(request);
        Evaluation evaluation = evaluationService.submitEvaluation(adviserId, evaluationId);
        return EvaluationResponse.fromEntity(evaluation);
    }

    /**
     * Save mixed team/individual evaluation from team questionnaire
     * Detects section type and routes to appropriate save method
     */
    @PostMapping("/evaluation/save-mixed/{evaluationId}")
    public ResponseEntity<?> saveMixedEvaluation(
            @PathVariable Long evaluationId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);
            Long teamId = Long.valueOf(payload.get("teamId").toString());
            Long questionnaireId = Long.valueOf(payload.get("questionnaireId").toString());
            String generalComments = (String) payload.get("generalComments");
            
            // Get team info
            Team team = teamService.getTeamById(teamId);
            Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                    .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

            // Parse section data: [ { sectionId, evaluateIndividuals, answers }, ... ]
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sectionDataList = (List<Map<String, Object>>) payload.get("sectionData");
            
            if (sectionDataList == null || sectionDataList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No section data provided"));
            }

            long teamSectionCount = 0;
            long individualSectionCount = 0;
            long teamStudentCount = team.getTeamStudents().size();

            for (Map<String, Object> sectionData : sectionDataList) {
                Boolean evaluateIndividuals = (Boolean) sectionData.getOrDefault("evaluateIndividuals", false);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> answersRaw = (Map<String, Object>) sectionData.get("answers");
                Map<Long, Object> answers = new HashMap<>();
                
                if (answersRaw != null) {
                    for (Map.Entry<String, Object> entry : answersRaw.entrySet()) {
                        try {
                            Long itemId = Long.valueOf(entry.getKey());
                            Object value = entry.getValue();
                            
                            // Validate answer values - should not be Map objects for flat evaluations
                            if (value instanceof Map && !evaluateIndividuals) {
                                log.warn("Skipping nested Map value for item {} in team-level evaluation. " +
                                        "Value: {}. This may indicate payload structure mismatch.", 
                                        itemId, value);
                                continue;
                            }
                            
                            answers.put(itemId, value);
                        } catch (NumberFormatException e) {
                            log.error("Invalid item ID in answers: {}", entry.getKey(), e);
                        }
                    }
                }

                if (!evaluateIndividuals) {
                    // TEAM-LEVEL: Save to Evaluation table (once per team)
                    evaluationService.saveEvaluation(adviserId, evaluationId, answers, generalComments);
                    teamSectionCount++;
                    log.info("Saved team-level answers for evaluation {} section with {} answers", evaluationId, answers.size());
                } else {
                    // INDIVIDUAL: Create StudentEvaluation for each student
                    // Convert studentIds from JSON (which are Integers) to Longs
                    @SuppressWarnings("unchecked")
                    List<Object> studentIdsRaw = (List<Object>) sectionData.getOrDefault("studentIds", 
                        team.getTeamStudents().stream()
                            .map(ts -> ts.getStudent().getId())
                            .collect(Collectors.toList()));
                    
                    List<Long> studentIds = new ArrayList<>();
                    for (Object id : studentIdsRaw) {
                        if (id instanceof Number) {
                            studentIds.add(((Number) id).longValue());
                        }
                    }

                    for (Long studentId : studentIds) {
                        StudentEvaluation eval = evaluationService
                                .getOrCreateAdviserStudentEvaluation(adviserId, teamId, studentId, questionnaireId);
                        
                        // Extract answers for this specific student from the section data
                        @SuppressWarnings("unchecked")
                        Map<String, Object> studentAnswersRaw = (Map<String, Object>) sectionData.get("studentAnswers");
                        Map<Long, Object> studentAnswers = new HashMap<>();
                        
                        if (studentAnswersRaw != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> perStudentAnswers = (Map<String, Object>) studentAnswersRaw.get(studentId.toString());
                            if (perStudentAnswers != null) {
                                for (Map.Entry<String, Object> entry : perStudentAnswers.entrySet()) {
                                    studentAnswers.put(Long.valueOf(entry.getKey()), entry.getValue());
                                }
                            }
                        }
                        
                        evaluationService.saveAdviserStudentEvaluation(adviserId, eval.getId(), studentAnswers);
                    }
                    individualSectionCount++;
                    log.info("Saved individual answers for evaluation {} section for {} students", 
                            evaluationId, studentIds.size());
                }
            }

            // If this is a submit request, mark the team-level evaluation as SUBMITTED
            Boolean isSubmit = Boolean.TRUE.equals(payload.get("submit"));
            if (isSubmit) {
                evaluationService.submitEvaluation(adviserId, evaluationId);
                // Also submit all adviser-student evals created for this questionnaire/team
                List<StudentEvaluation> adviserStudentEvals =
                        studentEvaluationRepository.findByAdviserIdAndTeamId(adviserId, teamId);
                for (StudentEvaluation studentEval : adviserStudentEvals) {
                    if (studentEval.getQuestionnaire() != null
                            && studentEval.getQuestionnaire().getId().equals(questionnaireId)
                            && studentEval.getStatus() == StudentEvaluation.EvaluationStatus.IN_PROGRESS) {
                        evaluationService.submitAdviserStudentEvaluation(adviserId, studentEval.getId());
                    }
                }
                log.info("Submitted mixed evaluation {} and associated student evals", evaluationId);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "evaluationId", evaluationId,
                    "teamSectionsSaved", teamSectionCount,
                    "individualSectionsSaved", individualSectionCount,
                    "studentEvaluationsCount", teamStudentCount * individualSectionCount));
        } catch (Exception e) {
            log.error("Error saving mixed evaluation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }

    @GetMapping("/evaluations/completed")
    public ResponseEntity<?> getCompletedEvaluations(HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);
            List<EvaluationResponse> evaluations = new ArrayList<>(
                    evaluationRepository.findByAdviserIdWithDetails(adviserId)).stream()
                    .filter(e -> e.getStatus() == Evaluation.EvaluationStatus.SUBMITTED)
                    .map(EvaluationResponse::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(evaluations);
        } catch (Exception e) {
            log.error("Error fetching completed evaluations: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // New adviser-to-student individual evaluation endpoints
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/teams/{teamId}/students")
    public ResponseEntity<?> getTeamStudents(
            @PathVariable Long teamId,
            HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);
            Team team = teamService.getTeamById(teamId);

            if (new ArrayList<>(team.getAdvisers()).stream().noneMatch(a -> a.getId().equals(adviserId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Unauthorized: Adviser not assigned to this team"));
            }

            List<Map<String, Object>> students = team.getTeamStudents().stream().map(ts -> {
                Map<String, Object> s = new HashMap<>();
                s.put("studentId", ts.getStudent().getId());
                s.put("firstName", ts.getStudent().getFirstName());
                s.put("lastName", ts.getStudent().getLastName());
                s.put("studentNumber", ts.getStudent().getStudentId());
                return s;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(students);
        } catch (Exception e) {
            log.error("Error fetching team students: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }

    @GetMapping("/student-eval/{teamId}/{studentId}/{questionnaireId}")
    public ResponseEntity<?> getOrCreateStudentEvaluation(
            @PathVariable Long teamId,
            @PathVariable Long studentId,
            @PathVariable Long questionnaireId,
            HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);
            StudentEvaluation eval = evaluationService
                    .getOrCreateAdviserStudentEvaluation(adviserId, teamId, studentId, questionnaireId);

            Questionnaire q = questionnaireRepository
                    .findByIdWithSectionsAndItems(questionnaireId)
                    .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

            Map<String, Object> response = new HashMap<>();
            response.put("id", eval.getId());
            response.put("status", eval.getStatus().name());
            response.put("teamId", teamId);
            response.put("evaluateeId", studentId);
            response.put("evaluateeFirstName", eval.getEvaluatee().getFirstName());
            response.put("evaluateeLastName", eval.getEvaluatee().getLastName());
            response.put("questionnaire", QuestionnaireWithItemsDto.fromEntity(q));
            response.put("submittedAt", eval.getSubmittedAt());
            response.put("createdAt", eval.getCreatedAt());
            response.put("updatedAt", eval.getUpdatedAt());
            response.put("scores", eval.getScores().stream().map(s -> {
                Map<String, Object> scoreMap = new HashMap<>();
                scoreMap.put("id", s.getId());
                scoreMap.put("questionnaireItemId", s.getQuestionnaireItem().getId());
                scoreMap.put("numericScore", s.getNumericScore());
                scoreMap.put("textResponse", s.getTextResponse());
                scoreMap.put("pointsAwarded", s.getPointsAwarded());
                return scoreMap;
            }).collect(Collectors.toList()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting student evaluation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }

    @PostMapping("/student-eval/save")
    public ResponseEntity<?> saveStudentEvaluation(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);
            Long evaluationId = Long.valueOf(payload.get("evaluationId").toString());

            Object answersObj = payload.get("answers");
            if (answersObj == null) {
                throw new RuntimeException("No answers provided in request");
            }
            
            if (!(answersObj instanceof Map)) {
                throw new RuntimeException("Answers must be a JSON object, got: " + answersObj.getClass().getSimpleName());
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> answersRaw = (Map<String, Object>) answersObj;
            Map<Long, Object> answers = new HashMap<>();
            for (Map.Entry<String, Object> entry : answersRaw.entrySet()) {
                try {
                    Long itemId = Long.valueOf(entry.getKey());
                    answers.put(itemId, entry.getValue());
                } catch (NumberFormatException e) {
                    log.error("Invalid item ID in answers: {}", entry.getKey(), e);
                    throw new RuntimeException("Invalid question ID: " + entry.getKey());
                }
            }

            StudentEvaluation saved = evaluationService
                    .saveAdviserStudentEvaluation(adviserId, evaluationId, answers);

            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "status", saved.getStatus().name(),
                    "updatedAt", saved.getUpdatedAt()));
        } catch (Exception e) {
            log.error("Error saving student evaluation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }

    @PostMapping("/student-eval/submit/{evaluationId}")
    public ResponseEntity<?> submitStudentEvaluation(
            @PathVariable Long evaluationId,
            HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);
            StudentEvaluation submitted = evaluationService
                    .submitAdviserStudentEvaluation(adviserId, evaluationId);

            return ResponseEntity.ok(Map.of(
                    "id", submitted.getId(),
                    "status", submitted.getStatus().name(),
                    "submittedAt", submitted.getSubmittedAt()));
        } catch (Exception e) {
            log.error("Error submitting student evaluation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }

    @GetMapping("/student-evaluations/completed")
    public ResponseEntity<?> getCompletedStudentEvaluations(HttpServletRequest request) {
        try {
            Long adviserId = getAdviserId(request);

            List<StudentEvaluation> evaluations = studentEvaluationRepository
                    .findByAdviserIdAndStatus(adviserId, StudentEvaluation.EvaluationStatus.SUBMITTED);

            List<Map<String, Object>> result = evaluations.stream().map(eval -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", eval.getId());
                map.put("evaluateeFirstName", eval.getEvaluatee() != null ? eval.getEvaluatee().getFirstName() : "");
                map.put("evaluateeLastName", eval.getEvaluatee() != null ? eval.getEvaluatee().getLastName() : "");
                map.put("studentNumber", eval.getEvaluatee() != null ? eval.getEvaluatee().getStudentId() : "");
                map.put("teamName", eval.getTeam() != null ? eval.getTeam().getName() : "");
                map.put("questionnaire", eval.getQuestionnaire() != null ? eval.getQuestionnaire().getTitle() : "");
                map.put("submittedAt", eval.getSubmittedAt());
                map.put("status", eval.getStatus().name());
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching completed student evaluations: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", getErrorMessage(e)));
        }
    }
}