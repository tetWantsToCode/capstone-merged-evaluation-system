package group9.advisor_eval_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(length = 5000)
    private String aiSummary; // AI-generated summary
    
    @Column(columnDefinition = "TEXT")
    private String performanceData; // JSON format with rankings, scores
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportType reportType;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime generatedAt;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by", nullable = false)
    private User generatedBy; // Teacher who generated the report
    
    public enum ReportType {
        CLASS_PERFORMANCE,
        TEAM_COMPARISON,
        INDIVIDUAL_TEAM,
        ADVISER_SUMMARY
    }
}