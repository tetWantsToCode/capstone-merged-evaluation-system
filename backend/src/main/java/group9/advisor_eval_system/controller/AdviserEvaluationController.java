package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.dto.EvaluationResponse;
import group9.advisor_eval_system.dto.EvaluationScoreDto;
import group9.advisor_eval_system.dto.QuestionnaireItemDto;
import group9.advisor_eval_system.dto.QuestionnaireWithItemsDto;
import group9.advisor_eval_system.entity.Evaluation;
import group9.advisor_eval_system.entity.Questionnaire;
import group9.advisor_eval_system.entity.QuestionnaireItem;
import group9.advisor_eval_system.entity.Team;
import group9.advisor_eval_system.repository.EvaluationRepository;
import group9.advisor_eval_system.repository.QuestionnaireItemRepository;
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

import java.util.List;
import java.util.Map;
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
    private final QuestionnaireItemRepository questionnaireItemRepository;

    private Long getAdviserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        return jwtUtil.extractUserId(token);
    }

    @GetMapping("/teams")
    public List<Team> getMyTeams(HttpServletRequest request) {
        Long adviserId = getAdviserId(request);
        return teamService.getAllTeams().stream()
                .filter(t -> t.getAdvisers().stream().anyMatch(a -> a.getId().equals(adviserId)))
                .toList();
    }

    @GetMapping("/teams/{teamId}/questionnaires")
    public List<Questionnaire> getTeamQuestionnaires(
            @PathVariable Long teamId,
            HttpServletRequest request
    ) {
        Long adviserId = getAdviserId(request);
        Team team = teamService.getTeamById(teamId);

        if (team.getAdvisers().stream().noneMatch(a -> a.getId().equals(adviserId))) {
            throw new RuntimeException("Unauthorized");
        }

        return questionnaireService.getQuestionnairesByClass(team.getSchoolClass().getId());
    }

    @GetMapping("/evaluation/{teamId}/{questionnaireId}")
    public ResponseEntity<?> getOrCreateEvaluation(
            @PathVariable Long teamId,
            @PathVariable Long questionnaireId,
            HttpServletRequest request
    ) {
        try {
            Long adviserId = getAdviserId(request);
            Evaluation evaluation = evaluationService.getOrCreateEvaluation(adviserId, teamId, questionnaireId);
            
            // Manually build response with items fetched directly from repository
            EvaluationResponse response = new EvaluationResponse();
            response.setId(evaluation.getId());
            response.setTeamId(evaluation.getTeam().getId());
            response.setTeamName(evaluation.getTeam().getName());
            response.setAdviserId(evaluation.getAdviser().getId());
            response.setAdviserName(evaluation.getAdviser().getFirstName() + " " + evaluation.getAdviser().getLastName());
            response.setStatus(evaluation.getStatus().name());
            response.setAllowEdit(evaluation.getAllowEdit());
            response.setGeneralComments(evaluation.getGeneralComments());
            response.setSubmittedAt(evaluation.getSubmittedAt());
            response.setCreatedAt(evaluation.getCreatedAt());
            response.setUpdatedAt(evaluation.getUpdatedAt());
            
            // Fetch questionnaire with items directly
            Questionnaire q = evaluation.getQuestionnaire();
            List<QuestionnaireItem> items = questionnaireItemRepository.findByQuestionnaireIdOrderByOrderIndex(q.getId());
            
            QuestionnaireWithItemsDto qDto = new QuestionnaireWithItemsDto();
            qDto.setId(q.getId());
            qDto.setTitle(q.getTitle());
            qDto.setDescription(q.getDescription());
            qDto.setGoogleFormUrl(q.getGoogleFormUrl());
            qDto.setItems(items.stream().map(QuestionnaireItemDto::fromEntity).collect(Collectors.toList()));
            
            response.setQuestionnaire(qDto);
            response.setScores(evaluation.getScores().stream().map(EvaluationScoreDto::fromEntity).collect(Collectors.toList()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/evaluation/save")
    public EvaluationResponse saveEvaluation(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
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
            HttpServletRequest request
    ) {
        Long adviserId = getAdviserId(request);
        Evaluation evaluation = evaluationService.submitEvaluation(adviserId, evaluationId);
        return EvaluationResponse.fromEntity(evaluation);
    }

    @GetMapping("/evaluations/completed")
    public List<EvaluationResponse> getCompletedEvaluations(HttpServletRequest request) {
        Long adviserId = getAdviserId(request);

        return evaluationRepository.findByAdviserId(adviserId).stream()
                .filter(e -> e.getStatus() == Evaluation.EvaluationStatus.SUBMITTED)
                .map(EvaluationResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
