package group9.advisor_eval_system.service;

import group9.advisor_eval_system.dto.AiChatRequest;
import group9.advisor_eval_system.entity.*;
import group9.advisor_eval_system.entity.Evaluation.EvaluationStatus;
import group9.advisor_eval_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

        private static final int MAX_SAMPLE_TEXT_PER_ITEM = 3;
        private static final int MAX_SAMPLE_GENERAL_COMMENTS = 5;

        private static final Set<String> OUT_OF_SCOPE_KEYWORDS = new HashSet<>(Arrays.asList(
                        "cook", "recipe", "food", "cooking", "baking", "dish", "cuisine",
                        "music", "song", "singer", "band", "concert",
                        "movie", "film", "actor", "actress", "cinema",
                        "sports", "basketball", "football", "soccer", "golf",
                        "travel", "vacation", "hotel", "airline", "destination",
                        "programming tutorial", "coding lesson", "software engineering course",
                        "math homework", "physics homework", "chemistry homework",
                        "pokemon", "gaming", "video game", "game", "stream",
                        "dating", "relationship", "love", "romance",
                        "joke", "funny", "meme", "laugh",
                        "weather", "forecast", "climate",
                        "news", "politics", "election",
                        "meditation", "yoga", "fitness", "workout",
                        "translate", "translation", "language",
                        "write a story", "write a poem", "write a song",
                        "generate image", "create image"));

        private static final Set<String> SYSTEM_SCOPE_KEYWORDS = new HashSet<>(Arrays.asList(
                        "evaluation", "questionnaire", "survey", "form", "assessment",
                        "adviser", "advisor", "feedback", "response", "report",
                        "respondent", "team", "performance", "score", "rating",
                        "question", "answer", "comment", "submission",
                        "rubric", "scale", "likert", "criteria", "benchmark",
                        "summary", "analysis", "insight", "trend", "average",
                        "strength", "weakness", "issue", "concern", "recommendation"));

        private final GeminiClient geminiClient;
        private final SchoolClassRepository schoolClassRepository;
        private final QuestionnaireRepository questionnaireRepository;
        private final EvaluationRepository evaluationRepository;
        private final StudentRepository studentRepository;
        private final StudentEvaluationRepository studentEvaluationRepository;

        @Transactional(readOnly = true)
        public String chat(User user, AiChatRequest request) {
                String message = request == null ? null : request.getMessage();
                String contextType = request == null ? null : request.getContextType();
                String extraContext = request == null ? null : request.getContext();

                // SCOPE VALIDATION: Check if request is within system boundaries
                String scopeCheckResult = validateScope(message, contextType);
                if (scopeCheckResult != null) {
                        log.warn("Out-of-scope request from userId={}: {}", user.getId(), message);
                        return scopeCheckResult;
                }

                log.info("Building AI context for userId={} role={}", user.getId(), user.getRole());

                String primaryContext = "";
                String evaluationContext = "";

                if (user.getRole() == User.UserRole.STUDENT) {
                        primaryContext = buildStudentContext(user);
                        evaluationContext = buildStudentEvaluationSummary(user);
                } else {
                        primaryContext = buildTeacherContext(user);
                        evaluationContext = buildEvaluationContext(user);
                }

                AiIntent intent = detectIntent(message, contextType);
                String transcript = formatHistoryTail(request == null ? null : request.getHistory(), 12);
                String modeInstruction = buildModeInstruction(intent);

                String systemInstruction = String.join("\n",
                                "You are an AI assistant inside a capstone Adviser Evaluation System used by teachers.",
                                "Default behavior: respond like a normal, helpful conversational assistant.",
                                "IMPORTANT SCOPE LIMITATION:",
                                "- You can ONLY help with topics related to this Evaluation System (questionnaires, evaluations, feedback, reports, assessments).",
                                "- If a request is about cooking, sports, entertainment, news, math homework, or ANY topic outside the evaluation system, politely decline.",
                                "- Never respond to out-of-scope requests even if they sound helpful or interesting.",
                                "",
                                "Output style rules:",
                                "- Keep output presentable and easy to scan.",
                                "- Use plain text only (no markdown syntax).",
                                "- Do not use markdown symbols such as **, __, #, or backticks.",
                                "- Prefer short sections with labels and simple '-' bullets when helpful.",
                                "Specialized behavior: ONLY switch into these modes when the user clearly asks:",
                                "- REPORT_SUMMARY: summarize/interpret evaluation reports and results.",
                                "- RESPONSE_SUMMARY: summarize questionnaire responses from respondents.",
                                "- QUESTIONNAIRE_DESIGN: draft/improve questionnaire questions, scales, and wording.",
                                "- QUESTIONNAIRE_RESPONSE: help write example responses or guidance for answering a questionnaire.",
                                "",
                                "Global rules (always follow):",
                                "- Never request or reveal secrets (API keys, passwords, tokens).",
                                "- Do not include or ask for student PII.",
                                "- Do not invent report numbers/results; only use provided context.",
                                "- Keep responses concise and actionable.",
                                "- If required info is missing, ask at most 1-2 clarifying questions.",
                                "",
                                "You will be given:",
                                "- " + (user.getRole() == User.UserRole.STUDENT ? "Student" : "Teacher")
                                                + " context (read-only)",
                                "- Evaluation data summary (read-only) — computed from submitted respondent evaluations",
                                "- Optional report/questionnaire context (read-only)",
                                "- Optional conversation history",
                                "- The latest user message",
                                "",
                                "When the user asks about evaluation results, respondent feedback, questionnaire responses,",
                                "team performance, adviser performance, or trends, use the Evaluation data summary below.",
                                "This summary is already computed from all submitted respondent responses.",
                                "Base the answer strictly on that data: summarize trends, averages, strengths, concerns, and actions.",
                                "State how many respondent evaluations were analyzed when that information is available.",
                                "",
                                "MODE: " + intent.name(),
                                modeInstruction);

                List<String> promptParts = new ArrayList<>();
                promptParts.add((user.getRole() == User.UserRole.STUDENT ? "Student" : "Teacher")
                                + " context (read-only):\n" + safeBlock(primaryContext));

                if (evaluationContext != null && !evaluationContext.isBlank()) {
                        promptParts.add("Evaluation data summary (read-only):\n" + safeBlock(evaluationContext));
                }

                if (extraContext != null && !extraContext.isBlank()) {
                        promptParts.add("Additional context (read-only):\n" + safeBlock(extraContext));
                }

                if (transcript != null && !transcript.isBlank()) {
                        promptParts.add("Conversation history (most recent last):\n" + transcript);
                }

                promptParts.add("Latest user message:\n" + (message == null ? "" : message));

                String userPrompt = String.join("\n\n---\n\n", promptParts);

                String rawReply = geminiClient.generateText(systemInstruction, userPrompt,
                                user.getAiApiKey(), user.getAiProvider());
                return makePresentableText(rawReply);
        }

        private enum AiIntent {
                GENERAL_CHAT,
                REPORT_SUMMARY,
                RESPONSE_SUMMARY,
                QUESTIONNAIRE_DESIGN,
                QUESTIONNAIRE_RESPONSE
        }

        private AiIntent detectIntent(String message, String contextType) {
                String normalized = (message == null ? "" : message).toLowerCase(Locale.ROOT);
                String normalizedCtx = (contextType == null ? "" : contextType).toLowerCase(Locale.ROOT);

                // If the client explicitly tags reports, prefer report mode.
                if (normalizedCtx.contains("report")) {
                        return AiIntent.REPORT_SUMMARY;
                }

                if (normalizedCtx.contains("response") || normalizedCtx.contains("feedback")
                                || normalizedCtx.contains("questionnaire")) {
                        return AiIntent.RESPONSE_SUMMARY;
                }

                // Response-focused summary questions.
                if (containsAny(normalized,
                                "respondent", "respondents", "response", "responses", "feedback", "comments",
                                "questionnaire result", "questionnaire responses", "analyze responses",
                                "scan responses", "what did they answer")) {
                        return AiIntent.RESPONSE_SUMMARY;
                }

                // Reports / evaluation results.
                if (containsAny(normalized,
                                "summarize", "summary", "report", "results", "evaluation report", "interpret",
                                "insight",
                                "trend", "average", "mean", "score", "ratings", "strengths", "weaknesses")) {
                        return AiIntent.REPORT_SUMMARY;
                }

                // Questionnaire design.
                if (containsAny(normalized,
                                "questionnaire", "survey", "form", "questions", "rubric", "likert", "scale",
                                "wording", "rephrase", "improve", "draft", "create", "items")) {
                        return AiIntent.QUESTIONNAIRE_DESIGN;
                }

                // Questionnaire responses / answering.
                if (containsAny(normalized,
                                "answer", "respond", "response", "fill out", "fill in", "sample answers",
                                "example response", "how should i answer")) {
                        return AiIntent.QUESTIONNAIRE_RESPONSE;
                }

                return AiIntent.GENERAL_CHAT;
        }

        private boolean containsAny(String haystack, String... needles) {
                if (haystack == null || haystack.isBlank()) {
                        return false;
                }
                for (String needle : needles) {
                        if (needle != null && !needle.isBlank() && haystack.contains(needle)) {
                                return true;
                        }
                }
                return false;
        }

        private String validateScope(String message, String contextType) {
                if (message == null || message.isBlank()) {
                        return null; // Allow empty messages
                }

                String normalized = message.toLowerCase(Locale.ROOT);

                // Check if message contains out-of-scope keywords
                for (String keyword : OUT_OF_SCOPE_KEYWORDS) {
                        if (normalized.contains(keyword)) {
                                return "I can only help with topics related to the Adviser Evaluation System, such as questionnaires, "
                                                +
                                                "evaluations, respondent feedback, reports, and performance analysis. "
                                                +
                                                "Unfortunately, I cannot assist with that request.";
                        }
                }

                // If it's clearly a system context (like from Reports or AI Assistant page),
                // allow it
                String normalizedCtx = (contextType == null ? "" : contextType.toLowerCase(Locale.ROOT));
                if (normalizedCtx.contains("report") || normalizedCtx.contains("response")
                                || normalizedCtx.contains("questionnaire")) {
                        return null; // In expected context
                }

                // For GENERAL_CHAT, check if message matches ANY system keywords
                // If it doesn't mention evaluation/system topics at all, it's likely
                // out-of-scope
                boolean hasSystemKeyword = false;
                for (String keyword : SYSTEM_SCOPE_KEYWORDS) {
                        if (normalized.contains(keyword)) {
                                hasSystemKeyword = true;
                                break;
                        }
                }

                // Allow if it has system keywords or is very short (greeting, etc.)
                if (!hasSystemKeyword && message.length() > 10) {
                        return "I'm here to help with the Adviser Evaluation System. " +
                                        "Please ask about questionnaires, evaluations, reports, adviser feedback, or assessment-related topics.";
                }

                return null; // Within scope
        }

        private String buildModeInstruction(AiIntent intent) {
                if (intent != AiIntent.RESPONSE_SUMMARY) {
                        return "";
                }

                return String.join("\n",
                                "RESPONSE_SUMMARY strict rules:",
                                "- Act as a summarizer only. Do not invent or assume missing data.",
                                "- Use only evidence present in the provided questionnaire answers and comments.",
                                "- Do not output unsupported labels such as Risks, Gaps, or Strengths unless explicitly grounded in responses.",
                                "- If evidence is missing for any section, write: Not enough data from responses.",
                                "- Keep wording factual and transparent; avoid speculation.",
                                "- When score data is provided, report it exactly as provided.",
                                "- If asked for a structured summary, follow this exact order:",
                                "  Overall Score:",
                                "  Performance Level:",
                                "  Key Strengths:",
                                "  Key Issues:",
                                "  Brief Summary:",
                                "  Suggested Actions:");
        }

        private String formatHistoryTail(List<AiChatRequest.ChatMessage> history, int maxItems) {
                if (history == null || history.isEmpty() || maxItems <= 0) {
                        return "";
                }

                int start = Math.max(0, history.size() - maxItems);
                return history.subList(start, history.size()).stream()
                                .filter(m -> m != null && m.getRole() != null && m.getText() != null)
                                .map(m -> {
                                        String role = m.getRole().trim().toLowerCase(Locale.ROOT);
                                        String label = role.equals("user") ? "User"
                                                        : role.equals("assistant") ? "Assistant" : "Other";
                                        return label + ": " + m.getText().trim();
                                })
                                .collect(Collectors.joining("\n"));
        }

        private String safeBlock(String text) {
                if (text == null) {
                        return "";
                }
                // Prevent extremely long blocks from bloating the prompt.
                int maxLen = 12000;
                String trimmed = text.trim();
                if (trimmed.length() <= maxLen) {
                        return trimmed;
                }
                return trimmed.substring(0, maxLen) + "\n...[truncated]";
        }

        private String buildTeacherContext(User user) {
                var classes = schoolClassRepository.findByTeacherId(user.getId());
                var questionnaires = questionnaireRepository.findByCreatedByTeacherIdAndIsActiveTrue(user.getId());

                String classSummary = classes.stream()
                                .limit(10)
                                .map(c -> formatClass(c))
                                .collect(Collectors.joining("; "));

                String questionnaireSummary = questionnaires.stream()
                                .limit(10)
                                .map(q -> q.getTitle())
                                .collect(Collectors.joining("; "));

                return String.join("\n",
                                "teacherId=" + user.getId(),
                                "role=" + user.getRole(),
                                "classes=" + (classSummary.isBlank() ? "(none)" : classSummary),
                                "questionnaires=" + (questionnaireSummary.isBlank() ? "(none)" : questionnaireSummary));
        }

        private String buildStudentContext(User user) {
                // Fetch student info
                Student student = studentRepository.findByEmail(user.getEmail()).orElse(null);
                if (student == null)
                        return "studentId=" + user.getId() + "\nrole=STUDENT\n(Profile not found)";

                String teamName = student.getTeamStudents() != null && !student.getTeamStudents().isEmpty()
                                ? student.getTeamStudents().get(0).getTeam().getName()
                                : "(no team)";

                return String.join("\n",
                                "studentId=" + user.getId(),
                                "name=" + student.getFirstName() + " " + student.getLastName(),
                                "role=STUDENT",
                                "team=" + teamName);
        }

        private String buildStudentEvaluationSummary(User user) {
                Student student = studentRepository.findByEmail(user.getEmail()).orElse(null);
                if (student == null)
                        return "No evaluation data found.";

                // This logic is similar to getStudentReportSummary in service
                // But I'll do it here to keep AiChatService self-contained or I could inject
                // the service.
                // For now, I'll implement a concise version.

                // For AI, we want a text summary of feedback received
                StringBuilder sb = new StringBuilder();
                sb.append("Feedback and scores RECEIVED by this student:\n");

                // Peer evaluations where student is evaluatee
                List<StudentEvaluation> evalsReceived = studentEvaluationRepository.findByEvaluateeIdAndStatus(
                                student.getId(), StudentEvaluation.EvaluationStatus.SUBMITTED);

                if (evalsReceived.isEmpty()) {
                        return "No peer evaluations have been submitted for you yet.";
                }

                Map<Long, List<Double>> qScores = new HashMap<>();
                Map<Long, String> qTitles = new HashMap<>();
                List<String> allComments = new ArrayList<>();

                for (StudentEvaluation eval : evalsReceived) {
                        qTitles.put(eval.getQuestionnaire().getId(), eval.getQuestionnaire().getTitle());
                        if (eval.getScores() != null) {
                                for (StudentEvaluationScore score : eval.getScores()) {
                                        if (score.getNumericScore() != null) {
                                                qScores.computeIfAbsent(eval.getQuestionnaire().getId(),
                                                                k -> new ArrayList<>()).add(score.getNumericScore());
                                        }
                                        if (score.getTextResponse() != null
                                                        && !score.getTextResponse().trim().isEmpty()) {
                                                allComments.add(score.getTextResponse());
                                        }
                                }
                        }
                }

                for (Long qId : qTitles.keySet()) {
                        sb.append("\nQuestionnaire: ").append(qTitles.get(qId)).append("\n");
                        List<Double> scores = qScores.get(qId);
                        if (scores != null && !scores.isEmpty()) {
                                double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                                sb.append("- Average score received from peers: ").append(String.format("%.2f", avg))
                                                .append("\n");
                        }
                }

                if (!allComments.isEmpty()) {
                        sb.append("\nComments received from peers:\n");
                        allComments.stream().distinct().limit(10)
                                        .forEach(c -> sb.append("- \"").append(c).append("\"\n"));
                }

                return sb.toString();
        }

        private String formatClass(SchoolClass c) {
                String section = (c.getSection() == null || c.getSection().isBlank()) ? ""
                                : (" (" + c.getSection() + ")");
                return c.getName() + section + " - " + c.getSchoolYear();
        }

        private String buildEvaluationContext(User teacher) {
                var questionnaires = questionnaireRepository.findByCreatedByTeacherIdAndIsActiveTrue(teacher.getId());
                if (questionnaires.isEmpty()) {
                        return "No active questionnaires found for this teacher.";
                }

                StringBuilder sb = new StringBuilder();
                int questionnairesWithResponses = 0;
                int totalSubmittedEvaluations = 0;
                int totalScoreRows = 0;

                for (var q : questionnaires) {
                        List<Evaluation> evaluations = evaluationRepository.findByQuestionnaireId(q.getId());
                        List<Evaluation> submitted = evaluations.stream()
                                        .filter(this::isSubmittedEvaluation)
                                        .collect(Collectors.toList());

                        if (submitted.isEmpty())
                                continue;

                        questionnairesWithResponses++;
                        totalSubmittedEvaluations += submitted.size();

                        List<QuestionnaireItem> items = q.getItems() != null
                                        ? q.getItems().stream()
                                                        .sorted(Comparator
                                                                        .comparingInt(QuestionnaireItem::getOrderIndex))
                                                        .collect(Collectors.toList())
                                        : Collections.emptyList();

                        Map<Long, QuestionAggregate> questionAggregates = new LinkedHashMap<>();
                        for (QuestionnaireItem item : items) {
                                questionAggregates.put(item.getId(), QuestionAggregate.fromItem(item));
                        }

                        Set<String> teams = new LinkedHashSet<>();
                        Set<String> advisers = new LinkedHashSet<>();
                        int generalCommentCount = 0;
                        List<String> generalCommentSamples = new ArrayList<>();
                        Set<String> seenGeneralComments = new HashSet<>();

                        for (Evaluation eval : submitted) {
                                if (eval.getTeam() != null && eval.getTeam().getName() != null) {
                                        teams.add(eval.getTeam().getName().trim());
                                }
                                if (eval.getAdviser() != null) {
                                        String first = eval.getAdviser().getFirstName() == null ? ""
                                                        : eval.getAdviser().getFirstName().trim();
                                        String last = eval.getAdviser().getLastName() == null ? ""
                                                        : eval.getAdviser().getLastName().trim();
                                        String adviserName = (first + " " + last).trim();
                                        if (!adviserName.isBlank()) {
                                                advisers.add(adviserName);
                                        }
                                }

                                String generalComment = normalizeText(eval.getGeneralComments());
                                if (generalComment != null) {
                                        generalCommentCount++;
                                        if (generalCommentSamples.size() < MAX_SAMPLE_GENERAL_COMMENTS
                                                        && seenGeneralComments.add(generalComment)) {
                                                generalCommentSamples.add(generalComment);
                                        }
                                }

                                Set<EvaluationScore> scores = eval.getScores();
                                if (scores == null || scores.isEmpty()) {
                                        continue;
                                }

                                for (EvaluationScore score : scores) {
                                        totalScoreRows++;
                                        QuestionnaireItem item = score.getQuestionnaireItem();
                                        if (item == null || item.getId() == null) {
                                                continue;
                                        }

                                        QuestionAggregate aggregate = questionAggregates.computeIfAbsent(
                                                        item.getId(),
                                                        ignored -> QuestionAggregate.fromItem(item));
                                        aggregate.addScore(score);
                                }
                        }

                        sb.append("\n## Questionnaire: ").append(q.getTitle()).append("\n");
                        if (q.getDescription() != null && !q.getDescription().isBlank()) {
                                sb.append("Description: ").append(q.getDescription()).append("\n");
                        }
                        sb.append("Submitted respondent evaluations: ").append(submitted.size()).append("\n");
                        sb.append("Distinct teams: ").append(teams.size())
                                        .append(teams.isEmpty() ? "" : " (" + summarizeCollection(teams, 6) + ")")
                                        .append("\n");
                        sb.append("Distinct advisers: ").append(advisers.size())
                                        .append(advisers.isEmpty() ? "" : " (" + summarizeCollection(advisers, 6) + ")")
                                        .append("\n");
                        sb.append("General comments count: ").append(generalCommentCount).append("\n");

                        if (!generalCommentSamples.isEmpty()) {
                                sb.append("Sample general comments:\n");
                                for (String sample : generalCommentSamples) {
                                        sb.append("  - \"").append(shorten(sample, 240)).append("\"\n");
                                }
                        }

                        sb.append("Question-level aggregates (computed from all submitted responses):\n");
                        for (QuestionAggregate aggregate : questionAggregates.values()) {
                                sb.append(aggregate.toSummaryLine()).append("\n");
                                if (!aggregate.sampleTextResponses.isEmpty()) {
                                        for (String sampleText : aggregate.sampleTextResponses) {
                                                sb.append("    Example response: \"")
                                                                .append(shorten(sampleText, 220))
                                                                .append("\"\n");
                                        }
                                }
                        }
                }

                if (questionnairesWithResponses == 0) {
                        return "No submitted evaluations found yet for this teacher's questionnaires.";
                }

                String header = String.join("\n",
                                "Coverage summary:",
                                "questionnairesWithResponses=" + questionnairesWithResponses,
                                "submittedRespondentEvaluationsAnalyzed=" + totalSubmittedEvaluations,
                                "scoreRowsScanned=" + totalScoreRows,
                                "allSubmittedResponsesScanned=true");

                sb.insert(0, header + "\n");

                return sb.toString();
        }

        private boolean isSubmittedEvaluation(Evaluation evaluation) {
                if (evaluation == null || evaluation.getStatus() == null) {
                        return false;
                }
                return evaluation.getStatus() == EvaluationStatus.SUBMITTED
                                || evaluation.getStatus() == EvaluationStatus.REVIEWED;
        }

        private String normalizeText(String value) {
                if (value == null) {
                        return null;
                }
                String trimmed = value.trim();
                return trimmed.isBlank() ? null : trimmed;
        }

        private String shorten(String value, int maxLength) {
                if (value == null) {
                        return "";
                }
                if (value.length() <= maxLength) {
                        return value;
                }
                return value.substring(0, maxLength) + "...";
        }

        private String summarizeCollection(Collection<String> values, int maxItems) {
                if (values == null || values.isEmpty()) {
                        return "";
                }
                return values.stream()
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .filter(v -> !v.isBlank())
                                .limit(maxItems)
                                .collect(Collectors.joining(", "));
        }

        private String makePresentableText(String value) {
                if (value == null) {
                        return "";
                }

                String text = value.replace("\r\n", "\n").trim();

                // Strip common markdown markers since the frontend renders plain text.
                text = text.replace("**", "");
                text = text.replace("__", "");
                text = text.replace("`", "");

                // Convert markdown headings and star bullets into plain-text equivalents.
                text = text.replaceAll("(?m)^#{1,6}\\s*", "");
                text = text.replaceAll("(?m)^\\*\\s+", "- ");

                // Keep spacing compact and readable.
                text = text.replaceAll("\\n{3,}", "\\n\\n");
                return text.trim();
        }

        private static final class QuestionAggregate {
                private final String questionText;
                private final QuestionnaireItem.QuestionType questionType;
                private final Integer minScore;
                private final Integer maxScore;

                private int numericCount = 0;
                private double numericSum = 0.0;
                private Double numericMin = null;
                private Double numericMax = null;

                private int textResponseCount = 0;
                private final List<String> sampleTextResponses = new ArrayList<>();
                private final Set<String> seenTextSamples = new HashSet<>();

                private QuestionAggregate(String questionText, QuestionnaireItem.QuestionType questionType,
                                Integer minScore, Integer maxScore) {
                        this.questionText = questionText;
                        this.questionType = questionType;
                        this.minScore = minScore;
                        this.maxScore = maxScore;
                }

                private static QuestionAggregate fromItem(QuestionnaireItem item) {
                        String text = item.getQuestionText() == null ? "(untitled question)" : item.getQuestionText();
                        QuestionnaireItem.QuestionType type = item.getQuestionType() == null
                                        ? QuestionnaireItem.QuestionType.TEXT
                                        : item.getQuestionType();
                        return new QuestionAggregate(text.trim(), type, item.getMinScore(), item.getMaxScore());
                }

                private void addScore(EvaluationScore score) {
                        if (score == null) {
                                return;
                        }

                        if (score.getNumericScore() != null) {
                                double value = score.getNumericScore();
                                numericCount++;
                                numericSum += value;
                                numericMin = numericMin == null ? value : Math.min(numericMin, value);
                                numericMax = numericMax == null ? value : Math.max(numericMax, value);
                        }

                        String response = score.getTextResponse();
                        if (response != null) {
                                String trimmed = response.trim();
                                if (!trimmed.isBlank()) {
                                        textResponseCount++;
                                        if (sampleTextResponses.size() < MAX_SAMPLE_TEXT_PER_ITEM
                                                        && seenTextSamples.add(trimmed)) {
                                                sampleTextResponses.add(trimmed);
                                        }
                                }
                        }
                }

                private String toSummaryLine() {
                        StringBuilder line = new StringBuilder();
                        line.append("  - Q: ").append(questionText)
                                        .append(" [").append(questionType.name()).append("]");

                        if (numericCount > 0) {
                                double average = numericSum / numericCount;
                                line.append(" | numericResponses=").append(numericCount)
                                                .append(" avg=")
                                                .append(String.format(Locale.US, "%.2f", average))
                                                .append(" min=")
                                                .append(String.format(Locale.US, "%.2f",
                                                                numericMin == null ? 0.0 : numericMin))
                                                .append(" max=")
                                                .append(String.format(Locale.US, "%.2f",
                                                                numericMax == null ? 0.0 : numericMax));

                                if (minScore != null || maxScore != null) {
                                        line.append(" scale=")
                                                        .append(minScore == null ? "?" : minScore)
                                                        .append("-")
                                                        .append(maxScore == null ? "?" : maxScore);
                                }
                        } else {
                                line.append(" | numericResponses=0");
                        }

                        line.append(" | textResponses=").append(textResponseCount);
                        return line.toString();
                }
        }
}