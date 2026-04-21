package group9.advisor_eval_system.repository;

import group9.advisor_eval_system.entity.Report;
import group9.advisor_eval_system.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findBySchoolClass(SchoolClass schoolClass);
    List<Report> findBySchoolClassId(Long classId);
    List<Report> findByReportType(Report.ReportType reportType);
}