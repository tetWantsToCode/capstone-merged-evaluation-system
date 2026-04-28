package group9.advisor_eval_system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AiChatRequest {

    @NotBlank(message = "message is required")
    @Size(max = 2000, message = "message must be at most 2000 characters")
    private String message;

    // Optional: client-provided conversation history to make responses coherent.
    // The server will keep only a small tail of this history.
    @Size(max = 20, message = "history must have at most 20 items")
    private List<@Valid ChatMessage> history;

    // Optional: additional contextual data (e.g., selected report details) as plain
    // text.
    @Size(max = 12000, message = "context must be at most 12000 characters")
    private String context;

    // Optional: hint about where the request came from (e.g., "ai-assistant",
    // "reports").
    @Size(max = 50, message = "contextType must be at most 50 characters")
    private String contextType;

    @Data
    public static class ChatMessage {
        @NotBlank(message = "role is required")
        @Size(max = 20, message = "role must be at most 20 characters")
        private String role;

        @NotBlank(message = "text is required")
        @Size(max = 2000, message = "text must be at most 2000 characters")
        private String text;
    }
}
