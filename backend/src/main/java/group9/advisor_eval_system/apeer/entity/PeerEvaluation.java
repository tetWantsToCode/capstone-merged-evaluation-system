package group9.advisor_eval_system.apeer.entity;

import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * APEER: One peer evaluation submission (evaluator scores one target member).
 * SRS Module 5.2 / SDD ERD: evaluations (eval_id, evaluator_id, target_student_id, activity_id, rubric_scores, comment_content, submitted_at).
 * Table name avoids conflict with existing evaluations (adviser evaluations).
 */
@Entity
@Table(name = "apeer_peer_evaluations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Student (or user) who submitted this evaluation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private User evaluator;

    /** Student being evaluated (target). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_student_id", nullable = false)
    private Student targetStudent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private EvaluationActivity activity;

    /** Criterion name -> score (0-10). Stored as JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rubric_scores", columnDefinition = "json")
    private Map<String, Integer> rubricScores;

    @Column(name = "comment_content", columnDefinition = "TEXT")
    private String commentContent;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
