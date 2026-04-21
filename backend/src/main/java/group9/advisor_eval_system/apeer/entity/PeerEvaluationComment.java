package group9.advisor_eval_system.apeer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * APEER: Comment linked to a peer evaluation. AI tagging (ai_tag) left for final phase; stored as null.
 * SRS Module 5.2 / SDD ERD: comments (comment_id, eval_id, content, ai_tag, tagged_at).
 */
@Entity
@Table(name = "apeer_comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerEvaluationComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eval_id", nullable = false, unique = true)
    private PeerEvaluation evaluation;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** Constructive / Vague / Off-topic — populated by AI in final phase; null for now. */
    @Column(name = "ai_tag", length = 50)
    private String aiTag;

    @Column(name = "tagged_at")
    private LocalDateTime taggedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
