package group9.advisor_eval_system.service;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import group9.advisor_eval_system.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GeminiClient {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3-flash-preview}")
    private String model;

    private volatile Client client;

    public String generateText(String systemInstruction, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Gemini is not configured: missing GEMINI_API_KEY");
        }

        try {
            Client sdkClient = getOrCreateClient();

            GenerateContentConfig config = null;
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                config = GenerateContentConfig.builder()
                        .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                        .temperature(0.7f)
                        .maxOutputTokens(1024)
                        .build();
            }

            log.info("Calling Gemini model='{}'", model);
            GenerateContentResponse response = sdkClient.models.generateContent(model, userPrompt, config);

            String text = response == null ? null : response.text();
            if (text == null || text.isBlank()) {
                throw new RuntimeException("Gemini returned an empty response");
            }

            return text.trim();
        } catch (Exception e) {
            if (e instanceof ExternalServiceException) {
                throw (ExternalServiceException) e;
            }
            throw new ExternalServiceException("Gemini error: " + e.getMessage(), e);
        }
    }

    private Client getOrCreateClient() {
        Client existing = client;
        if (existing != null) {
            return existing;
        }

        synchronized (this) {
            existing = client;
            if (existing != null) {
                return existing;
            }

            client = Client.builder().apiKey(apiKey).build();
            return client;
        }
    }
}
