package group9.advisor_eval_system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationAnalyticsResponse {

    // For chat mode: direct AI response
    private String aiResponse;

    // For evaluation_summary mode: structured report
    private OverallProgress overallProgress;
    private List<GroupSummary> groupSummaries;
    private RankingInsights rankingInsights;
    private ResponseMetrics responseMetrics;
    private CommentAnalysis commentAnalysis;
    private List<String> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverallProgress {
        private int totalRespondents;
        private int completedEvaluations;
        private double completionPercentage;
        private String status; // "Low", "Moderate", "High"
        private String statusInsight;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupSummary {
        private Long groupId;
        private String groupName;
        private int respondents;
        private int completedResponses;
        private double averageScore;
        private String performanceLevel; // "High", "Average", "Needs Improvement"
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankingInsights {
        private RespondentScore highestScorer;
        private RespondentScore lowestScorer;
        private String gaps; // Description of notable gaps
        private List<String> unusualResults;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RespondentScore {
        private String name;
        private double score;
        private String groupName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMetrics {
        private Map<String, Integer> completedPerGroup;
        private List<String> lowParticipationGroups;
        private int totalComments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentAnalysis {
        private List<String> keyThemes;
        private String overallSentiment; // "Positive", "Neutral", "Negative"
        private String summary;
        private List<String> commonFeedback;
    }
}
