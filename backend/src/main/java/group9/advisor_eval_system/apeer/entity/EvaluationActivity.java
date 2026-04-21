package group9.advisor_eval_system.apeer.entity;

import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * APEER: Peer evaluation session created by teacher.
 * SRS Module 5.1 / SDD ERD: evaluation_activities (activity_id, title, deadline, is_active, rubric_criteria, created_by).
 */
@Entity
@Table(name = "apeer_evaluation_activities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = true)
    private LocalDateTime deadline;

    @Column(nullable = false)
    private Boolean isActive = true;

    /** Rubric criteria names, e.g. ["Participation", "Cooperation", "Leadership"]. Stored as JSON array. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rubric_criteria", columnDefinition = "json")
    private List<String> rubricCriteria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdByTeacher;

    /** Class this activity is for; members = students in this class. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = true)
    private SchoolClass schoolClass;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
