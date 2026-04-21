package group9.advisor_eval_system.apeer.service;

import group9.advisor_eval_system.apeer.dto.ActivityResponse;
import group9.advisor_eval_system.apeer.dto.CreateActivityRequest;
import group9.advisor_eval_system.apeer.entity.EvaluationActivity;
import group9.advisor_eval_system.apeer.repository.EvaluationActivityRepository;
import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.SchoolClassRepository;
import group9.advisor_eval_system.repository.StudentRepository;
import group9.advisor_eval_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvaluationActivityService {

    private final EvaluationActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public ActivityResponse create(CreateActivityRequest request, Long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));
        EvaluationActivity activity = new EvaluationActivity();
        activity.setTitle(request.getTitle());
        activity.setRubricCriteria(request.getRubricCriteria() != null ? request.getRubricCriteria() : List.of());
        activity.setDeadline(request.getDeadline());
        activity.setIsActive(true);
        activity.setCreatedByTeacher(teacher);
        if (request.getClassId() != null) {
            SchoolClass schoolClass = schoolClassRepository.findById(request.getClassId())
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            activity.setSchoolClass(schoolClass);
        }
        activity = activityRepository.save(activity);
        return toResponse(activity);
    }

    public List<ActivityResponse> listByTeacher(Long teacherId) {
        return activityRepository.findByCreatedByTeacherIdOrderByCreatedAtDesc(teacherId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Activities available for a student (by user email) — activities in classes the student belongs to. */
    public List<ActivityResponse> listForStudent(String userEmail) {
        Student student = studentRepository.findByEmail(userEmail != null ? userEmail.toLowerCase().trim() : "")
                .orElse(null);
        if (student == null || student.getClasses() == null || student.getClasses().isEmpty()) {
            return List.of();
        }
        List<Long> classIds = student.getClasses().stream().map(SchoolClass::getId).collect(Collectors.toList());
        if (classIds.isEmpty()) return List.of();
        return activityRepository.findBySchoolClassIdInAndIsActiveTrueOrderByCreatedAtDesc(classIds)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ActivityResponse getById(Long id) {
        EvaluationActivity activity = activityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity not found"));
        return toResponse(activity);
    }

    public EvaluationActivity getEntityById(Long id) {
        return activityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activity not found"));
    }

    private ActivityResponse toResponse(EvaluationActivity a) {
        ActivityResponse r = new ActivityResponse();
        r.setId(a.getId());
        r.setTitle(a.getTitle());
        r.setDeadline(a.getDeadline());
        r.setIsActive(a.getIsActive());
        r.setRubricCriteria(a.getRubricCriteria());
        r.setCreatedByTeacherId(a.getCreatedByTeacher() != null ? a.getCreatedByTeacher().getId() : null);
        r.setClassId(a.getSchoolClass() != null ? a.getSchoolClass().getId() : null);
        r.setCreatedAt(a.getCreatedAt());
        return r;
    }
}
