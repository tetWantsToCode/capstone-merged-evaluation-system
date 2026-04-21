package group9.advisor_eval_system.exception;

/**
 * Used when a downstream/external integration fails (e.g., Gemini API).
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
