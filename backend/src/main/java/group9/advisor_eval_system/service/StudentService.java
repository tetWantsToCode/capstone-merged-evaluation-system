package group9.advisor_eval_system.service;

import group9.advisor_eval_system.dto.ImportStudentDTO;
import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.entity.Team;
import group9.advisor_eval_system.repository.SchoolClassRepository;
import group9.advisor_eval_system.repository.StudentRepository;
import group9.advisor_eval_system.repository.TeamRepository;
import group9.advisor_eval_system.util.ExcelImportUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class StudentService {
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    
    @Autowired
    private TeamRepository teamRepository;
    
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }
    
    public List<Student> getStudentsByTeacher(Long teacherId) {
        return studentRepository.findByCreatedBy(teacherId);
    }
    
    public Student getStudentById(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + id));
    }
    
    public Student createStudent(Student student, Long teacherId) {
        // Check if student ID already exists for this teacher
        if (studentRepository.existsByStudentIdAndCreatedBy(student.getStudentId(), teacherId)) {
            throw new RuntimeException("Student ID already exists: " + student.getStudentId());
        }
        student.setCreatedBy(teacherId);
        return studentRepository.save(student);
    }
    
    public Student updateStudent(Long id, Student studentDetails) {
        Student student = getStudentById(id);
        
        // Check if updating to a different student ID that already exists
        if (!student.getStudentId().equals(studentDetails.getStudentId()) 
                && studentRepository.existsByStudentId(studentDetails.getStudentId())) {
            throw new RuntimeException("Student ID already exists: " + studentDetails.getStudentId());
        }
        
        student.setStudentId(studentDetails.getStudentId());
        student.setFirstName(studentDetails.getFirstName());
        student.setLastName(studentDetails.getLastName());
        student.setEmail(studentDetails.getEmail());
        
        // Track removed classes BEFORE updating the classes list
        List<Long> oldClassIds = student.getClasses().stream()
                .map(SchoolClass::getId)
                .collect(java.util.stream.Collectors.toList());
        List<Long> removedClassIds = new ArrayList<>();
        
        // Handle classes assignment (many-to-many)
        if (studentDetails.getClasses() != null) {
            List<Long> newClassIds = new ArrayList<>();
            
            if (!studentDetails.getClasses().isEmpty()) {
                List<SchoolClass> classes = new ArrayList<>();
                for (SchoolClass cls : studentDetails.getClasses()) {
                    if (cls.getId() != null) {
                        SchoolClass schoolClass = schoolClassRepository.findById(cls.getId())
                            .orElseThrow(() -> new RuntimeException("Class not found with id: " + cls.getId()));
                        classes.add(schoolClass);
                        newClassIds.add(cls.getId());
                    }
                }
                student.setClasses(classes);
            } else {
                // Explicitly clear classes if empty array is sent
                student.setClasses(new ArrayList<>());
            }
            
            // Find removed classes
            removedClassIds = oldClassIds.stream()
                    .filter(classId -> !newClassIds.contains(classId))
                    .collect(java.util.stream.Collectors.toList());
        }
        // If studentDetails.getClasses() is null, don't touch the existing classes
        
        // Handle teams assignment (many-to-many)
        if (studentDetails.getTeams() != null) {
            if (!studentDetails.getTeams().isEmpty()) {
                List<Team> teams = new ArrayList<>();
                for (Team team : studentDetails.getTeams()) {
                    if (team.getId() != null) {
                        Team foundTeam = teamRepository.findById(team.getId())
                            .orElseThrow(() -> new RuntimeException("Team not found with id: " + team.getId()));
                        teams.add(foundTeam);
                    }
                }
                student.setTeams(teams);
            } else {
                // Explicitly clear teams if empty array is sent
                student.setTeams(new ArrayList<>());
            }
        }
        // If studentDetails.getTeams() is null, don't touch the existing teams
        
        // AFTER handling teams, remove student from teams in removed classes
        if (!removedClassIds.isEmpty()) {
            // Collect team IDs to remove
            List<Long> teamIdsToRemove = new ArrayList<>();
            
            for (Team team : student.getTeams()) {
                // Fetch full team with schoolClass to check
                Team fullTeam = teamRepository.findById(team.getId()).orElse(null);
                if (fullTeam != null && fullTeam.getSchoolClass() != null 
                        && removedClassIds.contains(fullTeam.getSchoolClass().getId())) {
                    teamIdsToRemove.add(team.getId());
                }
            }
            
            // Remove teams by filtering out the ones to remove
            if (!teamIdsToRemove.isEmpty()) {
                List<Team> updatedTeams = student.getTeams().stream()
                        .filter(team -> !teamIdsToRemove.contains(team.getId()))
                        .collect(java.util.stream.Collectors.toList());
                student.setTeams(updatedTeams);
            }
        }
        
        return studentRepository.save(student);
    }
    
    public void deleteStudent(Long id) {
        Student student = getStudentById(id);
        studentRepository.delete(student);
    }
    
    public ImportResult importStudentsFromExcel(MultipartFile file, Long teacherId) throws IOException {
        List<ImportStudentDTO> importedData = ExcelImportUtil.parseStudentsFromExcel(file);
        List<Student> createdStudents = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < importedData.size(); i++) {
            try {
                ImportStudentDTO dto = importedData.get(i);
                
                // Check if student ID already exists for this teacher
                if (studentRepository.existsByStudentIdAndCreatedBy(dto.getStudentId(), teacherId)) {
                    errors.add("Row " + (i + 2) + ": Student ID '" + dto.getStudentId() + "' already exists, skipped");
                    continue;
                }
                
                // Create new student
                Student student = new Student();
                student.setStudentId(dto.getStudentId());
                student.setFirstName(dto.getFirstName());
                student.setLastName(dto.getLastName());
                // Only set email if it looks valid, otherwise leave null
                String email = dto.getEmail();
                if (email != null && email.contains("@") && email.contains(".")) {
                    student.setEmail(email);
                }
                student.setCreatedBy(teacherId);
                
                Student savedStudent = studentRepository.save(student);
                createdStudents.add(savedStudent);
            } catch (Exception e) {
                errors.add("Row " + (i + 2) + ": " + e.getMessage());
            }
        }
        
        return new ImportResult(createdStudents, errors);
    }
    
    public static class ImportResult {
        private final List<Student> importedStudents;
        private final List<String> errors;
        
        public ImportResult(List<Student> importedStudents, List<String> errors) {
            this.importedStudents = importedStudents;
            this.errors = errors;
        }
        
        public List<Student> getImportedStudents() { return importedStudents; }
        public List<String> getErrors() { return errors; }
    }
}
