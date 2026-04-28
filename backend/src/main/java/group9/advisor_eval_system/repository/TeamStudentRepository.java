package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.TeamStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamStudentRepository extends JpaRepository<TeamStudent, Long> {
    List<TeamStudent> findByStudentId(Long studentId);
    List<TeamStudent> findByTeamId(Long teamId);
    Optional<TeamStudent> findByStudentIdAndTeamId(Long studentId, Long teamId);
    void deleteByStudentId(Long studentId);
    void deleteByStudentIdAndTeamId(Long studentId, Long teamId);
    void deleteByTeamId(Long teamId);
}
