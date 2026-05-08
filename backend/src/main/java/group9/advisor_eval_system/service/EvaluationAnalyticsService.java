package group9.advisor_eval_system.service;

import group9.advisor_eval_system.dto.EvaluationAnalyticsRequest;
import group9.advisor_eval_system.dto.EvaluationAnalyticsResponse;
import group9.advisor_eval_system.dto.EvaluationAnalyticsResponse.*;
import group9.advisor_eval_system.entity.*;
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
public class EvaluationAnalyticsService {

        private final EvaluationRepository evaluationRepository;
        private final GeminiClient geminiClient;

        /**
         * Generates a structured evaluation summary report for a questionnaire
         */
        @Transactional(readOnly = true)
        public EvaluationAnalyticsResponse generateEvaluationSummary(User teacher, EvaluationAnalyticsRequest request) {
                log.info("Generating evaluation summary for teacherId={} questionnaireId={}",
                                teacher.getId(), request.getQuestionnaireId());

                List<Evaluation> evaluations = getRelevantEvaluations(teacher, request);

                if (evaluations.isEmpty()) {
                        return new EvaluationAnalyticsResponse();
                }

                EvaluationAnalyticsResponse response = new EvaluationAnalyticsResponse();
                response.setOverallProgress(calculateOverallProgress(evaluations));
                response.setGroupSummaries(generateGroupSummaries(evaluations));
                response.setRankingInsights(generateRankingInsights(evaluations));
                response.setResponseMetrics(generateResponseMetrics(evaluations));
                response.setCommentAnalysis(analyzeComments(evaluations));
                response.setRecommendations(generateRecommendations(evaluations, response));

                return response;
        }

        /**
         * Handle chat-mode analytics queries
         */
        @Transactional(readOnly = true)
        public String handleAnalyticsQuery(User teacher, EvaluationAnalyticsRequest request) {
                log.info("Handling analytics query for teacherId={}", teacher.getId());

                List<Evaluation> evaluations = getRelevantEvaluations(teacher, request);
                String evaluationContext = buildEvaluationContext(evaluations);

                String systemInstruction = String.join("\n",
                                "You are an AI assistant helping teachers analyze evaluation data.",
                                "Base all responses strictly on provided evaluation data.",
                                "Provide clear, actionable insights.",
                                "Do not invent or assume missing data.",
                                "Keep responses concise and professional.");

                String userPrompt = String.join("\n\n",
                                "Evaluation data context:",
                                evaluationContext,
                                "",
                                "Teacher's question:",
                                request.getQuery() != null ? request.getQuery() : "");

                String rawReply = geminiClient.generateText(systemInstruction, userPrompt,
                                teacher.getAiApiKey(), teacher.getAiProvider());
                return makePresentableText(rawReply);
        }

        /**
         * Fetch relevant evaluations based on request filters
         */
        private List<Evaluation> getRelevantEvaluations(User teacher, EvaluationAnalyticsRequest request) {
                Long questionnaireId = request.getQuestionnaireId();
                Long teamId = request.getTeamId();

                List<Evaluation> evaluations = new ArrayList<>();

                if (questionnaireId != null) {
                        evaluations = evaluationRepository.findByQuestionnaireId(questionnaireId)
                                        .stream()
                                        .filter(e -> e.getAdviser().getId().equals(teacher.getId()))
                                        .collect(Collectors.toList());
                } else {
                        // Get all evaluations for this teacher
                        evaluations = evaluationRepository.findByAdviserId(teacher.getId());
                }

                // Filter by team if specified
                if (teamId != null) {
                        evaluations = evaluations.stream()
                                        .filter(e -> e.getTeam().getId().equals(teamId))
                                        .collect(Collectors.toList());
                }

                return evaluations;
        }

        /**
         * Calculate overall progress metrics
         */
        private OverallProgress calculateOverallProgress(List<Evaluation> evaluations) {
                if (evaluations.isEmpty()) {
                        return OverallProgress.builder()
                                        .totalRespondents(0)
                                        .completedEvaluations(0)
                                        .completionPercentage(0)
                                        .status("Low")
                                        .statusInsight("No evaluations found")
                                        .build();
                }

                long completed = evaluations.stream()
                                .filter(e -> e.getStatus() == Evaluation.EvaluationStatus.SUBMITTED)
                                .count();

                double percentage = (completed * 100.0) / evaluations.size();
                String status = percentage >= 80 ? "High" : percentage >= 50 ? "Moderate" : "Low";

                return OverallProgress.builder()
                                .totalRespondents(evaluations.size())
                                .completedEvaluations((int) completed)
                                .completionPercentage(Math.round(percentage * 100.0) / 100.0)
                                .status(status)
                                .statusInsight(String.format("%d of %d evaluations completed", completed,
                                                evaluations.size()))
                                .build();
        }

        /**
         * Generate group-level summaries
         */
        private List<GroupSummary> generateGroupSummaries(List<Evaluation> evaluations) {
                Map<String, List<Evaluation>> groupedByTeam = evaluations.stream()
                                .collect(Collectors
                                                .groupingBy(e -> e.getTeam().getName() != null ? e.getTeam().getName()
                                                                : "Unknown"));

                return groupedByTeam.entrySet().stream()
                                .map(entry -> {
                                        List<Evaluation> groupEvals = entry.getValue();
                                        long completed = groupEvals.stream()
                                                        .filter(e -> e.getStatus() == Evaluation.EvaluationStatus.SUBMITTED)
                                                        .count();

                                        double avgScore = calculateAverageScore(groupEvals);
                                        String performanceLevel = avgScore >= 3.5 ? "High"
                                                        : avgScore >= 2.5 ? "Average" : "Needs Improvement";

                                        return GroupSummary.builder()
                                                        .groupName(entry.getKey())
                                                        .respondents(groupEvals.size())
                                                        .completedResponses((int) completed)
                                                        .averageScore(Math.round(avgScore * 100.0) / 100.0)
                                                        .performanceLevel(performanceLevel)
                                                        .summary(String.format(
                                                                        "%d/%d respondents completed with avg score %.2f",
                                                                        completed, groupEvals.size(), avgScore))
                                                        .build();
                                })
                                .collect(Collectors.toList());
        }

        /**
         * Generate ranking insights
         */
        private RankingInsights generateRankingInsights(List<Evaluation> evaluations) {
                List<Evaluation> completed = evaluations.stream()
                                .filter(e -> e.getStatus() == Evaluation.EvaluationStatus.SUBMITTED)
                                .collect(Collectors.toList());

                if (completed.isEmpty()) {
                        return new RankingInsights();
                }

                // Calculate scores for ranking
                Map<Evaluation, Double> scores = completed.stream()
                                .collect(Collectors.toMap(e -> e, this::calculateEvaluationScore));

                Evaluation highest = scores.entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .orElse(null);

                Evaluation lowest = scores.entrySet().stream()
                                .min(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .orElse(null);

                double highestScore = highest != null ? scores.get(highest) : 0;
                double lowestScore = lowest != null ? scores.get(lowest) : 0;

                return RankingInsights.builder()
                                .highestScorer(highest != null ? RespondentScore.builder()
                                                .name(highest.getTeam().getName())
                                                .score(highestScore)
                                                .groupName(highest.getTeam().getName())
                                                .build() : null)
                                .lowestScorer(lowest != null ? RespondentScore.builder()
                                                .name(lowest.getTeam().getName())
                                                .score(lowestScore)
                                                .groupName(lowest.getTeam().getName())
                                                .build() : null)
                                .gaps(String.format("Score gap: %.2f (%.2f - %.2f)",
                                                highestScore - lowestScore, highestScore, lowestScore))
                                .unusualResults(identifyUnusualResults(scores))
                                .build();
        }

        /**
         * Generate response metrics
         */
        private ResponseMetrics generateResponseMetrics(List<Evaluation> evaluations) {
                Map<String, Integer> completedPerGroup = evaluations.stream()
                                .filter(e -> e.getStatus() == Evaluation.EvaluationStatus.SUBMITTED)
                                .collect(Collectors.groupingBy(
                                                e -> e.getTeam().getName() != null ? e.getTeam().getName() : "Unknown",
                                                Collectors.summingInt(e -> 1)));

                List<String> lowParticipationGroups = evaluations.stream()
                                .collect(Collectors.groupingBy(
                                                e -> e.getTeam().getName() != null ? e.getTeam().getName() : "Unknown"))
                                .entrySet().stream()
                                .filter(e -> e.getValue().stream()
                                                .filter(eval -> eval
                                                                .getStatus() == Evaluation.EvaluationStatus.SUBMITTED)
                                                .count() == 0)
                                .map(Map.Entry::getKey)
                                .toList();

                int totalComments = (int) evaluations.stream()
                                .filter(e -> e.getGeneralComments() != null && !e.getGeneralComments().isEmpty())
                                .count();

                return ResponseMetrics.builder()
                                .completedPerGroup(completedPerGroup)
                                .lowParticipationGroups(lowParticipationGroups)
                                .totalComments(totalComments)
                                .build();
        }

        /**
         * Analyze comments for themes and sentiment
         */
        private CommentAnalysis analyzeComments(List<Evaluation> evaluations) {
                List<String> comments = evaluations.stream()
                                .filter(e -> e.getGeneralComments() != null && !e.getGeneralComments().isEmpty())
                                .map(Evaluation::getGeneralComments)
                                .collect(Collectors.toList());

                if (comments.isEmpty()) {
                        return CommentAnalysis.builder()
                                        .keyThemes(new ArrayList<>())
                                        .overallSentiment("Neutral")
                                        .summary("No comments provided")
                                        .commonFeedback(new ArrayList<>())
                                        .build();
                }

                // Simple sentiment analysis
                String sentiment = estimateSentiment(comments);

                return CommentAnalysis.builder()
                                .keyThemes(extractThemes(comments))
                                .overallSentiment(sentiment)
                                .summary(String.format("%d comments analyzed", comments.size()))
                                .commonFeedback(extractCommonFeedback(comments))
                                .build();
        }

        /**
         * Generate actionable recommendations
         */
        private List<String> generateRecommendations(List<Evaluation> evaluations,
                        EvaluationAnalyticsResponse response) {
                List<String> recommendations = new ArrayList<>();

                // Low completion recommendation
                if (response.getOverallProgress().getCompletionPercentage() < 50) {
                        recommendations
                                        .add("Follow up with advisers who have not yet submitted evaluations to improve completion rate");
                }

                // Low participation groups
                if (!response.getResponseMetrics().getLowParticipationGroups().isEmpty()) {
                        recommendations.add("Prioritize getting feedback from: " +
                                        String.join(", ", response.getResponseMetrics().getLowParticipationGroups()));
                }

                // Performance improvement
                boolean hasNeedsImprovement = response.getGroupSummaries().stream()
                                .anyMatch(g -> "Needs Improvement".equals(g.getPerformanceLevel()));
                if (hasNeedsImprovement) {
                        recommendations
                                        .add("Identify teams with 'Needs Improvement' ratings and provide targeted support or resources");
                }

                // Sentiment-based recommendation
                if ("Negative".equals(response.getCommentAnalysis().getOverallSentiment())) {
                        recommendations.add("Address concerns raised in evaluations through direct feedback sessions");
                }

                // Consistency check
                if (response.getRankingInsights() != null && response.getRankingInsights().getGaps() != null) {
                        recommendations.add(
                                        "Review scoring consistency across evaluators to ensure fair and uniform assessments");
                }

                // Ensure we have at least basic recommendations
                if (recommendations.isEmpty()) {
                        recommendations.add("Continue monitoring team performance trends");
                        recommendations.add("Maintain current evaluation frequency for consistent feedback");
                }

                return recommendations.stream()
                                .limit(5)
                                .collect(Collectors.toList());
        }

        /**
         * Helper: Calculate average score for a set of evaluations
         */
        private double calculateAverageScore(List<Evaluation> evaluations) {
                return evaluations.stream()
                                .mapToDouble(this::calculateEvaluationScore)
                                .average()
                                .orElse(0);
        }

        /**
         * Helper: Calculate single evaluation score from scores
         */
        private double calculateEvaluationScore(Evaluation evaluation) {
                if (evaluation.getScores() == null || evaluation.getScores().isEmpty()) {
                        return 0;
                }
                return evaluation.getScores().stream()
                                .mapToDouble(score -> score.getNumericScore() != null ? score.getNumericScore() : 0)
                                .average()
                                .orElse(0);
        }

        /**
         * Helper: Identify unusual scoring results
         */
        private List<String> identifyUnusualResults(Map<Evaluation, Double> scores) {
                if (scores.isEmpty())
                        return new ArrayList<>();

                double avg = scores.values().stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElse(0);

                double stdDev = Math.sqrt(scores.values().stream()
                                .mapToDouble(s -> Math.pow(s - avg, 2))
                                .average()
                                .orElse(0));

                return scores.entrySet().stream()
                                .filter(e -> Math.abs(e.getValue() - avg) > 2 * stdDev)
                                .map(e -> String.format("%s scored %.2f (significant outlier)",
                                                e.getKey().getTeam().getName(), e.getValue()))
                                .toList();
        }

        /**
         * Helper: Extract key themes from comments
         */
        private List<String> extractThemes(List<String> comments) {
                Map<String, Integer> keywords = new HashMap<>();
                String[] commonKeywords = { "excellent", "good", "needs", "improvement", "support",
                                "strength", "weakness", "collaboration", "communication", "leadership" };

                for (String comment : comments) {
                        String lower = comment.toLowerCase();
                        for (String keyword : commonKeywords) {
                                if (lower.contains(keyword)) {
                                        keywords.put(keyword, keywords.getOrDefault(keyword, 0) + 1);
                                }
                        }
                }

                return keywords.entrySet().stream()
                                .filter(e -> e.getValue() > 1)
                                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                                .limit(5)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList());
        }

        /**
         * Helper: Extract common feedback patterns
         */
        private List<String> extractCommonFeedback(List<String> comments) {
                return comments.stream()
                                .filter(c -> c.length() > 20)
                                .limit(3)
                                .collect(Collectors.toList());
        }

        /**
         * Helper: Estimate overall sentiment from comments
         */
        private String estimateSentiment(List<String> comments) {
                int positive = 0, negative = 0;
                String[] positiveWords = { "excellent", "good", "great", "strong", "impressive", "outstanding" };
                String[] negativeWords = { "poor", "weak", "needs", "lacking", "concerning", "inadequate" };

                for (String comment : comments) {
                        String lower = comment.toLowerCase();
                        for (String word : positiveWords) {
                                if (lower.contains(word))
                                        positive++;
                        }
                        for (String word : negativeWords) {
                                if (lower.contains(word))
                                        negative++;
                        }
                }

                if (positive > negative)
                        return "Positive";
                if (negative > positive)
                        return "Negative";
                return "Neutral";
        }

        /**
         * Helper: Build evaluation context for AI
         */
        private String buildEvaluationContext(List<Evaluation> evaluations) {
                StringBuilder context = new StringBuilder();
                context.append("Questionnaire Evaluation Summary:\n");
                context.append("Total Evaluations: ").append(evaluations.size()).append("\n");
                context.append("Submitted: ").append(evaluations.stream()
                                .filter(e -> e.getStatus() == Evaluation.EvaluationStatus.SUBMITTED)
                                .count()).append("\n\n");

                context.append("Group Performance:\n");
                Map<String, List<Evaluation>> grouped = evaluations.stream()
                                .collect(Collectors
                                                .groupingBy(e -> e.getTeam().getName() != null ? e.getTeam().getName()
                                                                : "Unknown"));

                grouped.forEach((group, evals) -> {
                        double avgScore = calculateAverageScore(evals);
                        context.append("- ").append(group).append(": ")
                                        .append(String.format("%.2f avg score, %d responses",
                                                        avgScore, evals.size()))
                                        .append("\n");
                });

                context.append("\nComments:\n");
                evaluations.stream()
                                .filter(e -> e.getGeneralComments() != null)
                                .limit(10)
                                .forEach(e -> context.append("- ").append(e.getGeneralComments()).append("\n"));

                return context.toString();
        }

        /**
         * Helper: Make text presentable
         */
        private String makePresentableText(String text) {
                if (text == null)
                        return "";
                return text.replaceAll("\\*\\*", "").replaceAll("__", "").replaceAll("`", "");
        }
}
