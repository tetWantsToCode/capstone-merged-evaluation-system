package group9.advisor_eval_system.apeer.service;

import group9.advisor_eval_system.apeer.entity.PeerEvaluation;
import group9.advisor_eval_system.apeer.repository.PeerEvaluationRepository;
import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.repository.StudentRepository;
import group9.advisor_eval_system.service.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * APEER: AI-powered summarization of peer feedback for a student.
 * SRS REQ-601 to REQ-607: collect comments, de-duplicate, summarize via NLP,
 * categorize into strengths / weaknesses / suggestions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeerFeedbackSummaryService {

    private final PeerEvaluationRepository peerEvaluationRepository;
    private final StudentRepository studentRepository;
    private final GeminiClient geminiClient;

    private static final String SYSTEM_INSTRUCTION =
            "You are an academic feedback summarizer for a student peer evaluation system. " +
            "Given a list of peer feedback comments about a student, produce a structured summary. " +
            "Your response MUST be valid JSON with exactly these three keys: " +
            "\"strengths\" (array of strings), \"weaknesses\" (array of strings), \"suggestions\" (array of strings). " +
            "Each array should have 2–4 concise, student-appropriate bullet points. " +
            "Remove duplicate or near-duplicate ideas. Be constructive and professional. " +
            "Do not include raw comments — synthesize and summarize them. " +
            "Return ONLY the JSON object, no markdown fences, no extra text.";

    /**
     * Generates an AI summary for a student identified by their User email.
     * REQ-601: collects all comments; REQ-604: deduplicates; REQ-602/603: summarizes and categorizes.
     */
    public Map<String, Object> generateSummaryForStudent(String studentEmail) {
        Student student = studentRepository.findByEmail(studentEmail != null ? studentEmail.toLowerCase().trim() : "")
                .orElse(null);
        if (student == null) {
            return buildEmpty("No student profile found for this account.");
        }

        List<PeerEvaluation> evaluations = peerEvaluationRepository.findByTargetStudentId(student.getId());
        if (evaluations.isEmpty()) {
            return buildEmpty("No peer evaluations have been submitted for you yet.");
        }

        List<String> comments = evaluations.stream()
                .map(PeerEvaluation::getCommentContent)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toList());

        if (comments.isEmpty()) {
            return buildEmpty("Peer evaluations exist but no text comments have been submitted yet.");
        }

        List<String> deduped = deduplicateComments(comments);

        String prompt = buildPrompt(student.getFirstName(), deduped);

        try {
            String raw = geminiClient.generateText(SYSTEM_INSTRUCTION, prompt);
            Map<String, Object> parsed = parseJson(raw);
            parsed.put("studentName", student.getFirstName() + " " + student.getLastName());
            parsed.put("totalEvaluations", evaluations.size());
            parsed.put("status", "success");
            return parsed;
        } catch (Exception e) {
            log.warn("Gemini summarization failed: {}", e.getMessage());
            return buildEmpty("AI summary is temporarily unavailable. Please try again later.");
        }
    }

    private String buildPrompt(String firstName, List<String> comments) {
        StringBuilder sb = new StringBuilder();
        sb.append("Student name: ").append(firstName).append("\n\n");
        sb.append("Peer feedback comments (").append(comments.size()).append(" total):\n");
        for (int i = 0; i < comments.size(); i++) {
            sb.append(i + 1).append(". ").append(comments.get(i)).append("\n");
        }
        return sb.toString();
    }

    /** Simple deduplication: keeps a comment if it is not very similar to any already-kept comment. */
    private List<String> deduplicateComments(List<String> comments) {
        List<String> result = new ArrayList<>();
        for (String c : comments) {
            String normalized = c.toLowerCase().replaceAll("\\s+", " ").trim();
            boolean duplicate = result.stream().anyMatch(kept -> {
                String kNorm = kept.toLowerCase().replaceAll("\\s+", " ").trim();
                return kNorm.equals(normalized) || similarity(kNorm, normalized) > 0.85;
            });
            if (!duplicate) {
                result.add(c);
            }
        }
        return result;
    }

    /** Jaccard similarity on word sets as a simple near-duplicate heuristic. */
    private double similarity(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split("\\s+")));
        if (setA.isEmpty() && setB.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```[a-zA-Z]*", "").replace("```", "").trim();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(cleaned, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse Gemini JSON response, returning raw: {}", e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("strengths", List.of(cleaned));
            fallback.put("weaknesses", List.of());
            fallback.put("suggestions", List.of());
            return fallback;
        }
    }

    private Map<String, Object> buildEmpty(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "no_data");
        result.put("message", message);
        result.put("strengths", List.of());
        result.put("weaknesses", List.of());
        result.put("suggestions", List.of());
        return result;
    }
}
