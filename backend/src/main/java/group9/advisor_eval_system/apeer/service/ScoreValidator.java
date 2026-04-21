package group9.advisor_eval_system.apeer.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates peer evaluation scores per SRS 5.2:
 * - Each score must be between 0 and 10 inclusive.
 * - Within each criterion, all scores assigned to different members must be unique (checked at service level with existing submissions).
 */
@Component
public class ScoreValidator {

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 10;

    public ValidationResult validate(Map<String, Integer> rubricScores, List<String> activityCriteria) {
        List<String> errors = new ArrayList<>();
        if (rubricScores == null || rubricScores.isEmpty()) {
            errors.add("At least one rubric score is required.");
            return new ValidationResult(errors);
        }
        for (String criterion : activityCriteria) {
            Integer score = rubricScores.get(criterion);
            if (score == null) {
                errors.add("Score for criterion '" + criterion + "' is required.");
                continue;
            }
            if (score < MIN_SCORE || score > MAX_SCORE) {
                errors.add("Score for '" + criterion + "' must be between " + MIN_SCORE + " and " + MAX_SCORE + ".");
            }
        }
        for (String key : rubricScores.keySet()) {
            if (activityCriteria != null && !activityCriteria.contains(key)) {
                errors.add("Unknown criterion: " + key);
            }
        }
        return new ValidationResult(errors);
    }

    public static class ValidationResult {
        private final List<String> errors;

        public ValidationResult(List<String> errors) {
            this.errors = errors != null ? errors : new ArrayList<>();
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }
}
