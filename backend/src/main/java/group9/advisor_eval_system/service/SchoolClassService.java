package group9.advisor_eval_system.service;

import group9.advisor_eval_system.entity.Questionnaire;
import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.entity.Team;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.QuestionnaireRepository;
import group9.advisor_eval_system.repository.SchoolClassRepository;
import group9.advisor_eval_system.repository.StudentRepository;
import group9.advisor_eval_system.repository.TeamRepository;
import group9.advisor_eval_system.repository.UserRepository;

import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

@Service
public class SchoolClassService {
    
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private QuestionnaireRepository questionnaireRepository;
    
    @Autowired
    private TeamRepository teamRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<SchoolClass> getAllClasses() {
        return schoolClassRepository.findAll();
    }
    
    public List<SchoolClass> getClassesByTeacher(Long teacherId) {
        return schoolClassRepository.findByTeacherId(teacherId);
    }
    
    public List<SchoolClass> getActiveClasses() {
        return schoolClassRepository.findByIsActiveTrue();
    }
    
    public SchoolClass getClassById(Long id) {
        return schoolClassRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Class not found with id: " + id));
    }
    
    public SchoolClass createClass(SchoolClass schoolClass) {
        // For session-based auth with frontend localStorage approach:
        // The teacher should be set by the controller from the frontend request
        // or we fallback to finding any teacher
        
        if (schoolClass.getTeacher() == null || schoolClass.getTeacher().getId() == null) {
            // Fallback: use first available teacher
            User teacher = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == User.UserRole.TEACHER)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No teacher users found in database"));
            schoolClass.setTeacher(teacher);
        } else {
            // Teacher ID was provided from frontend, fetch the full user object
            User teacher = userRepository.findById(schoolClass.getTeacher().getId())
                    .orElseThrow(() -> new RuntimeException("Teacher not found with id: " + schoolClass.getTeacher().getId()));
            
            // Verify it's actually a teacher
            if (teacher.getRole() != User.UserRole.TEACHER) {
                throw new RuntimeException("Only teachers can create classes");
            }
            
            schoolClass.setTeacher(teacher);
        }
        
        return schoolClassRepository.save(schoolClass);
    }
    
    public SchoolClass updateClass(Long id, SchoolClass classDetails) {
        SchoolClass schoolClass = getClassById(id);
        
        schoolClass.setName(classDetails.getName());
        schoolClass.setSection(classDetails.getSection());
        schoolClass.setSchoolYear(classDetails.getSchoolYear());
        schoolClass.setDescription(classDetails.getDescription());
        schoolClass.setIsActive(classDetails.getIsActive());
        
        return schoolClassRepository.save(schoolClass);
    }
    
    @Transactional
    public void deleteClass(Long id) {
        System.out.println("Attempting to delete class with ID: " + id);
        SchoolClass schoolClass = null;
        try {
            schoolClass = schoolClassRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Class not found with id: " + id));
            System.out.println("Found class: " + schoolClass.getName());
        } catch (Exception e) {
            System.out.println("Error finding class: " + e.getMessage());
            throw e;
        }
        
        // Clear any direct class_id foreign key in students table (legacy constraint)
        entityManager.createNativeQuery("UPDATE students SET class_id = NULL WHERE class_id = :classId")
                .setParameter("classId", id)
                .executeUpdate();
        
        // Remove this class from all students' class lists (many-to-many)
        if (schoolClass.getStudents() != null && !schoolClass.getStudents().isEmpty()) {
            for (Student student : new ArrayList<>(schoolClass.getStudents())) {
                student.getClasses().remove(schoolClass);
                studentRepository.save(student);
            }
        }
        
        // Remove this class from all questionnaires
        List<Questionnaire> questionnaires = questionnaireRepository.findAll();
        for (Questionnaire questionnaire : questionnaires) {
            if (questionnaire.getAssignedClasses() != null && questionnaire.getAssignedClasses().contains(schoolClass)) {
                questionnaire.getAssignedClasses().remove(schoolClass);
                questionnaireRepository.save(questionnaire);
            }
        }
        
        // Manually delete all teams associated with this class
        List<Team> teams = teamRepository.findBySchoolClassId(id);
        if (teams != null && !teams.isEmpty()) {
            // Get all students to check for team memberships
            List<Student> allStudents = studentRepository.findAll();
            for (Team team : teams) {
                // Remove this team from all students' team lists
                for (Student student : allStudents) {
                    if (student.getTeams() != null && student.getTeams().contains(team)) {
                        student.getTeams().remove(team);
                        studentRepository.save(student);
                    }
                }
            }
            // Now delete all teams
            teamRepository.deleteAll(teams);
        }
        
        schoolClassRepository.delete(schoolClass);
    }
}
