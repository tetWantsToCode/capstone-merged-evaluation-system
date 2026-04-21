package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.dto.AiChatRequest;
import group9.advisor_eval_system.dto.AiChatResponse;
import group9.advisor_eval_system.dto.EvaluationAnalyticsRequest;
import group9.advisor_eval_system.dto.EvaluationAnalyticsResponse;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.UserRepository;
import group9.advisor_eval_system.service.AiChatService;
import group9.advisor_eval_system.service.EvaluationAnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiChatService aiChatService;
    private final EvaluationAnalyticsService evaluationAnalyticsService;
    private final UserRepository userRepository;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @Valid @RequestBody AiChatRequest request,
            Authentication authentication) {

        User user = getUserFromAuthentication(authentication);

        int messageLength = request.getMessage() == null ? 0 : request.getMessage().length();
        int historySize = request.getHistory() == null ? 0 : request.getHistory().size();
        String contextType = request.getContextType() == null ? "" : request.getContextType();

        log.info("AI chat request userId={} role={} messageLength={} historySize={} contextType={}",
                user.getId(), user.getRole(), messageLength, historySize, contextType);

        if (user.getRole() != User.UserRole.TEACHER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only teachers can use the AI assistant"));
        }

        String reply = aiChatService.chat(user, request);
        return ResponseEntity.ok(new AiChatResponse(reply));
    }

    @PostMapping("/analytics")
    public ResponseEntity<?> analyzeEvaluations(
            @Valid @RequestBody EvaluationAnalyticsRequest request,
            Authentication authentication) {

        User user = getUserFromAuthentication(authentication);

        log.info("Analytics request userId={} mode={} questionnaireId={}",
                user.getId(), request.getMode(), request.getQuestionnaireId());

        if (user.getRole() != User.UserRole.TEACHER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only teachers can use analytics"));
        }

        try {
            if ("chat".equals(request.getMode())) {
                // Chat mode: user asking a question about evaluations
                String response = evaluationAnalyticsService.handleAnalyticsQuery(user, request);
                return ResponseEntity.ok(new AiChatResponse(response));
            } else if ("evaluation_summary".equals(request.getMode())) {
                // Auto analysis mode: generate structured report
                EvaluationAnalyticsResponse response = evaluationAnalyticsService.generateEvaluationSummary(user,
                        request);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Invalid mode. Must be 'chat' or 'evaluation_summary'"));
            }
        } catch (Exception e) {
            log.error("Error processing analytics request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error processing request: " + e.getMessage()));
        }
    }

    @GetMapping("/analytics/summary/{questionnaireId}")
    public ResponseEntity<?> getEvaluationSummary(
            @PathVariable Long questionnaireId,
            Authentication authentication) {

        User user = getUserFromAuthentication(authentication);

        log.info("Request evaluation summary for questionnaireId={} userId={}",
                questionnaireId, user.getId());

        if (user.getRole() != User.UserRole.TEACHER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("Only teachers can access analytics"));
        }

        try {
            EvaluationAnalyticsRequest request = new EvaluationAnalyticsRequest();
            request.setMode("evaluation_summary");
            request.setQuestionnaireId(questionnaireId);

            EvaluationAnalyticsResponse response = evaluationAnalyticsService.generateEvaluationSummary(user, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error generating summary: " + e.getMessage()));
        }
    }

    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Unauthenticated");
        }

        Object principal = authentication.getPrincipal();
        Long userId;

        if (principal instanceof Long) {
            userId = (Long) principal;
        } else if (principal instanceof Integer) {
            userId = ((Integer) principal).longValue();
        } else {
            userId = Long.parseLong(principal.toString());
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
