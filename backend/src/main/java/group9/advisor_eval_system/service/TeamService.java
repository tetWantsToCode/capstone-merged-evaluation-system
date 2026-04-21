package group9.advisor_eval_system.service;

import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.entity.Team;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.SchoolClassRepository;
import group9.advisor_eval_system.repository.StudentRepository;
import group9.advisor_eval_system.repository.TeamRepository;
import group9.advisor_eval_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TeamService {
    
    @Autowired
    private TeamRepository teamRepository;
    
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    public List<Team> getAllTeams() {
        return teamRepository.findAll();
    }
    
    public List<Team> getTeamsByClass(Long classId) {
        return teamRepository.findBySchoolClassId(classId);
    }
    
    public List<Team> getActiveTeams() {
        return teamRepository.findByIsActiveTrue();
    }
    
    public Team getTeamById(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Team not found with id: " + id));
    }
    
    public Team createTeam(Team team) {
        // Validate and set school class
        if (team.getSchoolClass() == null || team.getSchoolClass().getId() == null) {
            throw new RuntimeException("Team must be assigned to a class");
        }
        
        SchoolClass schoolClass = schoolClassRepository.findById(team.getSchoolClass().getId())
                .orElseThrow(() -> new RuntimeException("Class not found with id: " + team.getSchoolClass().getId()));
        team.setSchoolClass(schoolClass);
        
        // Clear members and advisers - they should be empty on creation
        team.setMembers(new ArrayList<>());
        team.setAdvisers(new ArrayList<>());
        
        // Save the team first
        Team savedTeam = teamRepository.save(team);
        
        return savedTeam;
    }
    
    public Team updateTeam(Long id, Team teamDetails) {
        Team team = getTeamById(id);
        
        team.setName(teamDetails.getName());
        team.setDescription(teamDetails.getDescription());
        team.setIsActive(teamDetails.getIsActive());
        
        // Handle members update - ManyToMany relationship
        // Student is the owning side, so we need to update student.teams
        if (teamDetails.getMembers() != null) {
            // First, remove this team from all current members
            for (Student currentMember : new ArrayList<>(team.getMembers())) {
                currentMember.getTeams().remove(team);
                studentRepository.save(currentMember);
            }
            
            List<Student> newMembers = new ArrayList<>();
            
            for (Student member : teamDetails.getMembers()) {
                if (member.getId() != null) {
                    Student student = studentRepository.findById(member.getId())
                            .orElseThrow(() -> new RuntimeException("Student not found with id: " + member.getId()));
                    
                    // Validate: student can only be in one team per class
                    for (Team existingTeam : student.getTeams()) {
                        if (existingTeam.getSchoolClass().getId().equals(team.getSchoolClass().getId()) 
                            && !existingTeam.getId().equals(team.getId())) {
                            throw new RuntimeException("Student " + student.getFirstName() + " " + student.getLastName() 
                                + " is already in another team in this class");
                        }
                    }
                    
                    // Add this team to the student's teams list (owning side)
                    if (!student.getTeams().contains(team)) {
                        student.getTeams().add(team);
                        studentRepository.save(student);
                    }
                    
                    newMembers.add(student);
                }
            }
            team.setMembers(newMembers);
        }
        
        // Handle advisers update - ManyToMany relationship
        if (teamDetails.getAdvisers() != null) {
            List<User> advisers = new ArrayList<>();
            for (User adviser : teamDetails.getAdvisers()) {
                if (adviser.getId() != null) {
                    User user = userRepository.findById(adviser.getId())
                            .orElseThrow(() -> new RuntimeException("Adviser not found with id: " + adviser.getId()));
                    if (user.getRole() != User.UserRole.ADVISER) {
                        throw new RuntimeException("User with id " + user.getId() + " is not an adviser");
                    }
                    advisers.add(user);
                }
            }
            team.setAdvisers(advisers);
        }
        
        return teamRepository.save(team);
    }
    
    public void deleteTeam(Long id) {
        Team team = getTeamById(id);
        teamRepository.delete(team);
    }
}
