package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.dto.EvaluationResponse;
import group9.advisor_eval_system.dto.EvaluationScoreDto;
import group9.advisor_eval_system.dto.QuestionnaireWithItemsDto;
import group9.advisor_eval_system.entity.Evaluation;
import group9.advisor_eval_system.entity.EvaluationScore;
import group9.advisor_eval_system.entity.Questionnaire;
import group9.advisor_eval_system.entity.Team;
import group9.advisor_eval_system.repository.EvaluationRepository;
import group9.advisor_eval_system.repository.EvaluationScoreRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
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
                if (evaluation.getQuestionnaire() != null && evaluation.getQuestionnaire().getItems() != null) {
                    totalQuestions = new HashSet<>(evaluation.getQuestionnaire().getItems()).size();
                }

                int answeredCount = evaluation.getScores() != null ? evaluation.getScores().size() : 0;
                int progressPercent = totalQuestions > 0
                        ? (int) Math.round((answeredCount * 100.0) / totalQuestions)
                        : 0;

                String queueStatus;
                if (evaluation.getStatus() == Evaluation.EvaluationStatus.SUBMITTED ||
                        evaluation.getStatus() == Evaluation.EvaluationStatus.REVIEWED) {
                    queueStatus = "SUBMITTED";
                } else if (evaluation.getQuestionnaire() != null
                        && Boolean.TRUE.equals(evaluation.getQuestionnaire().getIsLocked())) {
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
                log.error("Evaluation returned null from service");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Failed to create or retrieve evaluation"));
            }

            // Ensure relationships are loaded
            if (evaluation.getAdviser() == null) {
                log.error("Adviser is null on evaluation");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Adviser data is missing from evaluation"));
            }

            if (evaluation.getTeam() == null) {
                log.error("Team is null on evaluation");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Team data is missing from evaluation"));
            }

            if (evaluation.getQuestionnaire() == null) {
                log.error("Questionnaire is null on evaluation");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Questionnaire data is missing from evaluation"));
            }

            // Manually build response with items fetched directly from repository
            EvaluationResponse response = new EvaluationResponse();
            response.setId(evaluation.getId());
            response.setTeamId(evaluation.getTeam().getId());
            response.setTeamName(evaluation.getTeam().getName());
            response.setAdviserId(evaluation.getAdviser().getId());

            String adviserName = (evaluation.getAdviser().getFirstName() != null
                    ? evaluation.getAdviser().getFirstName()
                    : "")
                    + " "
                    + (evaluation.getAdviser().getLastName() != null ? evaluation.getAdviser().getLastName() : "");
            response.setAdviserName(adviserName.trim());
            response.setStatus(evaluation.getStatus() != null ? evaluation.getStatus().name() : "UNKNOWN");
            response.setAllowEdit(evaluation.getAllowEdit() != null ? evaluation.getAllowEdit() : false);
            response.setGeneralComments(evaluation.getGeneralComments());
            response.setSubmittedAt(evaluation.getSubmittedAt());
            response.setCreatedAt(evaluation.getCreatedAt());
            response.setUpdatedAt(evaluation.getUpdatedAt());

            log.info("Response allowEdit={}, status={}", response.getAllowEdit(), response.getStatus());

            // Fetch questionnaire with sections and items eagerly loaded to avoid lazy
            // loading issues
            Questionnaire q = questionnaireRepository
                    .findByIdWithSectionsAndItems(evaluation.getQuestionnaire().getId())
                    .orElseThrow(() -> new RuntimeException("Questionnaire not found"));

            // Use the fromEntity method which properly handles both items and sections
            QuestionnaireWithItemsDto qDto = QuestionnaireWithItemsDto.fromEntity(q);

            response.setQuestionnaire(qDto);

            // Fetch scores directly to avoid lazy loading issues
            List<EvaluationScore> scores = evaluationScoreRepository.findByEvaluationId(evaluation.getId());
            response.setScores(
                    new ArrayList<>(scores).stream().map(EvaluationScoreDto::fromEntity).collect(Collectors.toList()));

            int totalItems = (qDto.getItems() != null ? qDto.getItems().size() : 0) +
                    (qDto.getSections() != null
                            ? qDto.getSections().stream().mapToInt(s -> s.getItems() != null ? s.getItems().size() : 0)
                                    .sum()
                            : 0);
            log.info("Successfully retrieved evaluation with {} items in {} sections", totalItems,
                    qDto.getSections() != null ? qDto.getSections().size() : 0);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting evaluation: {}", e.getMessage(), e);
            String errorMsg = getErrorMessage(e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", errorMsg));
        }
    }

    @PostMapping("/evaluation/save")
    public EvaluationResponse saveEvaluation(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        Long adviserId = getAdviserId(request);

        Long evaluationId = Long.valueOf(payload.get("evaluationId").toString());
        String generalComments = (String) payload.get("generalComments");

        // Convert answers map from String keys to Long keys
        Map<String, Object> answersRaw = (Map<String, Object>) payload.get("answers");
        Map<Long, Object> answers = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : answersRaw.entrySet()) {
            answers.put(Long.valueOf(entry.getKey()), entry.getValue());
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
}
