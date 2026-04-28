package group9.advisor_eval_system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentReportSummaryDto {
    private String studentName;
    private String teamName;
    private List<QuestionnaireSummary> summaries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionnaireSummary {
        private Long questionnaireId;
        private String questionnaireTitle;
        private Double overallAverage;
        private List<CategoryScore> categoryScores;
        private List<String> feedbackComments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryScore {
        private String categoryName;
        private Double averageScore;
        private Integer responseCount;
    }
}
