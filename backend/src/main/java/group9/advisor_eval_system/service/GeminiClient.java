package group9.advisor_eval_system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import group9.advisor_eval_system.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiClient {

    @Value("${gemini.model:gemini-2.0-flash}")
    private String defaultGeminiModel;

    @Value("${gemini.api.key:}")
    private String defaultApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Generate text using the user-provided API key and provider.
     * Falls back to the server default Gemini key when the user has no key configured.
     * Supported providers: "gemini", "openai", "anthropic".
     */
    public String generateText(String systemInstruction, String userPrompt,
            String userApiKey, String userProvider) {

        // Resolve key: prefer user's own key, fall back to server default (Gemini)
        String resolvedKey = (userApiKey != null && !userApiKey.isBlank()) ? userApiKey : defaultApiKey;
        String resolvedProvider = resolvedKey.equals(defaultApiKey) ? "gemini"
                : (userProvider != null && !userProvider.isBlank() ? userProvider.toLowerCase().trim() : "gemini");

        if (resolvedKey == null || resolvedKey.isBlank()) {
            throw new RuntimeException(
                    "AI is not configured. Please add your AI API key in Profile > AI Settings.");
        }

        String provider = resolvedProvider;

        try {
            return switch (provider) {
                case "openai" -> callOpenAi(resolvedKey, systemInstruction, userPrompt);
                case "anthropic" -> callAnthropic(resolvedKey, systemInstruction, userPrompt);
                default -> callGemini(resolvedKey, systemInstruction, userPrompt);
            };
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException(
                    "AI error (" + provider + "): " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Provider implementations
    // -------------------------------------------------------------------------

    private String callGemini(String apiKey, String systemInstruction, String userPrompt) {
        Client sdkClient = Client.builder().apiKey(apiKey).build();

        GenerateContentConfig config = null;
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                    .temperature(0.7f)
                    .maxOutputTokens(1024)
                    .build();
        }

        log.info("Calling Gemini model='{}'", defaultGeminiModel);
        GenerateContentResponse response = sdkClient.models.generateContent(defaultGeminiModel, userPrompt, config);

        String text = response == null ? null : response.text();
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Gemini returned an empty response");
        }
        return text.trim();
    }

    private String callOpenAi(String apiKey, String systemInstruction, String userPrompt)
            throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemInstruction));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("model", "gpt-4o-mini");
        bodyMap.put("messages", messages);
        bodyMap.put("max_tokens", 1024);
        bodyMap.put("temperature", 0.7);

        String body = objectMapper.writeValueAsString(bodyMap);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new ExternalServiceException(
                    "OpenAI API error " + response.statusCode() + ": " + response.body(), null);
        }

        JsonNode json = objectMapper.readTree(response.body());
        String text = json.path("choices").get(0).path("message").path("content").asText();
        if (text == null || text.isBlank()) {
            throw new RuntimeException("OpenAI returned an empty response");
        }
        return text.trim();
    }

    private String callAnthropic(String apiKey, String systemInstruction, String userPrompt)
            throws Exception {
        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("model", "claude-3-5-haiku-latest");
        bodyMap.put("max_tokens", 1024);
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            bodyMap.put("system", systemInstruction);
        }
        bodyMap.put("messages",
                List.of(Map.of("role", "user", "content", userPrompt)));

        String body = objectMapper.writeValueAsString(bodyMap);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new ExternalServiceException(
                    "Anthropic API error " + response.statusCode() + ": " + response.body(), null);
        }

        JsonNode json = objectMapper.readTree(response.body());
        String text = json.path("content").get(0).path("text").asText();
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Anthropic returned an empty response");
        }
        return text.trim();
    }
}
