package group9.advisor_eval_system.service;

import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.entity.Team;
import group9.advisor_eval_system.repository.SchoolClassRepository;
import group9.advisor_eval_system.repository.StudentRepository;
import group9.advisor_eval_system.repository.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    
    public Student getStudentById(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found with id: " + id));
    }
    
    public Student createStudent(Student student) {
        // Check if student ID already exists
        if (studentRepository.existsByStudentId(student.getStudentId())) {
            throw new RuntimeException("Student ID already exists: " + student.getStudentId());
        }
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
        student.setPhoneNumber(studentDetails.getPhoneNumber());
        
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
}
