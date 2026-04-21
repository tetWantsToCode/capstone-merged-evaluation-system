package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.dto.AssignQuestionnaireRequest;
import group9.advisor_eval_system.dto.CreateQuestionnaireRequest;
import group9.advisor_eval_system.dto.QuestionnaireResponse;
import group9.advisor_eval_system.dto.UpdateQuestionnaireStatusRequest;
import group9.advisor_eval_system.entity.Questionnaire;
import group9.advisor_eval_system.entity.QuestionnaireItem;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.QuestionnaireItemRepository;
import group9.advisor_eval_system.repository.UserRepository;
import group9.advisor_eval_system.service.QuestionnaireService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/questionnaires")
@RequiredArgsConstructor
@Slf4j
public class QuestionnaireController {

    private final QuestionnaireService questionnaireService;
    private final UserRepository userRepository;
    private final QuestionnaireItemRepository questionnaireItemRepository;

    /**
     * Create a new questionnaire (Teacher only)
     */
    @PostMapping
    public ResponseEntity<?> createQuestionnaire(
            @Valid @RequestBody CreateQuestionnaireRequest request,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);

            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Only teachers can create questionnaires"));
            }

            List<QuestionnaireItem> questions = request.getQuestions() != null
                    ? request.getQuestions().stream()
                            .map(CreateQuestionnaireRequest.QuestionnaireItemDto::toEntity)
                            .collect(Collectors.toList())
                    : List.of();

            Questionnaire questionnaire = questionnaireService.createQuestionnaire(
                    user.getId(),
                    request.getTitle(),
                    request.getDescription(),
                    questions,
                    request.getSections() != null ? request.getSections() : List.of());

            QuestionnaireResponse response = QuestionnaireResponse.fromEntity(questionnaire);
            // Set the actual question count from database
            long count = questionnaireItemRepository.countByQuestionnaireId(questionnaire.getId());
            response.setQuestionCount((int) count);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(response);

        } catch (Exception e) {
            log.error("Error creating questionnaire", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Get all questionnaires for the current user
     * - Teachers: Get questionnaires they created
     * - Advisers: Get questionnaires assigned to their teams' classes
     */
    @GetMapping
    public ResponseEntity<?> getQuestionnaires(Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);

            log.info("Fetching questionnaires for user {} with role {}", user.getId(), user.getRole());

            List<Questionnaire> questionnaires;
            if (user.getRole() == User.UserRole.TEACHER) {
                questionnaires = questionnaireService.getQuestionnairesByTeacher(user.getId());
            } else {
                questionnaires = questionnaireService.getQuestionnairesForAdviser(user.getId());
            }

            List<QuestionnaireResponse> responses = questionnaires.stream()
                    .map(q -> {
                        QuestionnaireResponse response = QuestionnaireResponse.fromEntity(q);
                        long count = questionnaireItemRepository.countByQuestionnaireId(q.getId());
                        response.setQuestionCount((int) count);
                        return response;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error fetching questionnaires: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Get a specific questionnaire by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getQuestionnaireById(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            getUserFromAuthentication(authentication);

            Questionnaire questionnaire = questionnaireService.getQuestionnaireById(id);
            return ResponseEntity.ok(QuestionnaireResponse.fromEntity(questionnaire));

        } catch (Exception e) {
            log.error("Error fetching questionnaire", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Get questionnaires for a specific class
     */
    @GetMapping("/class/{classId}")
    public ResponseEntity<?> getQuestionnairesByClass(
            @PathVariable Long classId,
            Authentication authentication) {
        try {
            getUserFromAuthentication(authentication);

            List<Questionnaire> questionnaires = questionnaireService.getQuestionnairesByClass(classId);
            List<QuestionnaireResponse> responses = questionnaires.stream()
                    .map(q -> {
                        QuestionnaireResponse response = QuestionnaireResponse.fromEntity(q);
                        // Directly query the count from database
                        long count = questionnaireItemRepository.countByQuestionnaireId(q.getId());
                        response.setQuestionCount((int) count);
                        return response;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error fetching questionnaires for class", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Get questionnaires for a specific class for teachers (includes inactive)
     */
    @GetMapping("/class/{classId}/teacher")
    public ResponseEntity<?> getQuestionnairesByClassForTeacher(
            @PathVariable Long classId,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);

            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Only teachers can access this endpoint"));
            }

            List<Questionnaire> questionnaires = questionnaireService.getQuestionnairesByClassForTeacher(classId, user.getId());
            List<QuestionnaireResponse> responses = questionnaires.stream()
                    .map(q -> {
                        QuestionnaireResponse response = QuestionnaireResponse.fromEntity(q);
                        long count = questionnaireItemRepository.countByQuestionnaireId(q.getId());
                        response.setQuestionCount((int) count);
                        return response;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error fetching questionnaires for teacher class", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Assign questionnaire to classes (Teacher only)
     */
    @PostMapping("/{id}/assign")
    public ResponseEntity<?> assignToClasses(
            @PathVariable Long id,
            @Valid @RequestBody AssignQuestionnaireRequest request,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);

            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Only teachers can assign questionnaires"));
            }

            Questionnaire questionnaire = questionnaireService.assignToClasses(
                    id,
                    request.getClassIds(),
                    user.getId());

            return ResponseEntity.ok(QuestionnaireResponse.fromEntity(questionnaire));

        } catch (Exception e) {
            log.error("Error assigning questionnaire to classes", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Remove questionnaire from classes (Teacher only)
     */
    @PostMapping("/{id}/unassign")
    public ResponseEntity<?> removeFromClasses(
            @PathVariable Long id,
            @Valid @RequestBody AssignQuestionnaireRequest request,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);

            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Only teachers can unassign questionnaires"));
            }

            Questionnaire questionnaire = questionnaireService.removeFromClasses(
                    id,
                    request.getClassIds(),
                    user.getId());

            return ResponseEntity.ok(QuestionnaireResponse.fromEntity(questionnaire));

        } catch (Exception e) {
            log.error("Error removing questionnaire from classes", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Activate/deactivate questionnaire (Teacher only)
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateQuestionnaireStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuestionnaireStatusRequest request,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);

            Questionnaire questionnaire = questionnaireService.updateQuestionnaireStatus(
                    id,
                    request.getIsActive(),
                    user.getId());

            return ResponseEntity.ok(QuestionnaireResponse.fromEntity(questionnaire));

        } catch (Exception e) {
            log.error("Error updating questionnaire status", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Update questionnaire details (Teacher only)
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateQuestionnaire(
            @PathVariable Long id,
            @Valid @RequestBody CreateQuestionnaireRequest request,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);

            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Only teachers can update questionnaires"));
            }

            Questionnaire questionnaire = questionnaireService.updateQuestionnaire(
                    id,
                    request.getTitle(),
                    request.getDescription(),
                    user.getId());

            return ResponseEntity.ok(QuestionnaireResponse.fromEntity(questionnaire));

        } catch (Exception e) {
            log.error("Error updating questionnaire", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Delete questionnaire (Teacher only)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQuestionnaire(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);

            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Only teachers can delete questionnaires"));
            }

            questionnaireService.deleteQuestionnaire(id, user.getId());

            return ResponseEntity.ok(new SuccessResponse("Questionnaire deleted successfully"));

        } catch (Exception e) {
            log.error("Error deleting questionnaire", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Debug endpoint to check questionnaire items
     */
    @GetMapping("/{id}/items")
    public ResponseEntity<?> getQuestionnaireItems(@PathVariable Long id) {
        try {
            Questionnaire questionnaire = questionnaireService.getQuestionnaireById(id);
            long itemCount = questionnaireItemRepository.countByQuestionnaireId(id);

            return ResponseEntity.ok(Map.of(
                    "questionnaireId", id,
                    "title", questionnaire.getTitle(),
                    "itemCount", itemCount,
                    "items", questionnaire.getItems() != null ? questionnaire.getItems().size() : 0));
        } catch (Exception e) {
            log.error("Error getting questionnaire items", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update questionnaire item with correct answer and points (Teacher only)
     */
    @PutMapping("/{questionnaireId}/items/{itemId}")
    public ResponseEntity<?> updateQuestionnaireItem(
            @PathVariable Long questionnaireId,
            @PathVariable Long itemId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);

            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Only teachers can update questions"));
            }

            String questionText = (String) request.get("questionText");
            String correctAnswer = (String) request.get("correctAnswer");
            Integer pointsValue = request.get("pointsValue") != null ? 
                ((Number) request.get("pointsValue")).intValue() : null;

            QuestionnaireItem updatedItem = questionnaireService.updateQuestionnaireItem(
                    questionnaireId,
                    itemId,
                    questionText,
                    correctAnswer,
                    pointsValue,
                    user.getId());

            return ResponseEntity.ok(Map.of(
                    "id", updatedItem.getId(),
                    "questionText", updatedItem.getQuestionText(),
                    "correctAnswer", updatedItem.getCorrectAnswer(),
                    "pointsValue", updatedItem.getPointsValue()
            ));

        } catch (Exception e) {
            log.error("Error updating questionnaire item", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Get questionnaire lock status
     */
    @GetMapping("/{id}/lock-status")
    public ResponseEntity<?> getLockStatus(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            User user = getUserFromAuthentication(authentication);
            Questionnaire questionnaire = questionnaireService.getQuestionnaireById(id);

            // Only teacher who created it can check lock status
            if (user.getRole() == User.UserRole.TEACHER 
                && !questionnaire.getCreatedByTeacher().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("You can only view lock status for your own questionnaires"));
            }

            return ResponseEntity.ok(Map.of(
                    "questionnaireId", id,
                    "isLocked", questionnaire.getIsLocked() != null && questionnaire.getIsLocked(),
                    "lockedAt", questionnaire.getLockedAt()
            ));

        } catch (Exception e) {
            log.error("Error getting lock status", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
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

    public static class SuccessResponse {
        private String message;

        public SuccessResponse(String message) {
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
