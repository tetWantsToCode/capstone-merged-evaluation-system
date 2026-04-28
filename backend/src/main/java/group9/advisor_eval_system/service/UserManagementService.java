package group9.advisor_eval_system.service;

import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.entity.Team;
import group9.advisor_eval_system.entity.Evaluation;
import group9.advisor_eval_system.entity.Questionnaire;
import group9.advisor_eval_system.entity.Report;
import group9.advisor_eval_system.entity.Student;
import group9.advisor_eval_system.entity.TeamStudent;
import group9.advisor_eval_system.repository.UserRepository;
import group9.advisor_eval_system.repository.SchoolClassRepository;
import group9.advisor_eval_system.repository.TeamRepository;
import group9.advisor_eval_system.repository.EvaluationRepository;
import group9.advisor_eval_system.repository.QuestionnaireRepository;
import group9.advisor_eval_system.repository.ReportRepository;
import group9.advisor_eval_system.repository.StudentRepository;
import group9.advisor_eval_system.repository.TeamStudentRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserManagementService {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private QuestionnaireRepository questionnaireRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeamStudentRepository teamStudentRepository;

    public static class UploadResult {
        private int added;
        private int updated;
        private int skipped;
        private List<String> errors;

        public UploadResult(int added, int updated, int skipped, List<String> errors) {
            this.added = added;
            this.updated = updated;
            this.skipped = skipped;
            this.errors = errors;
        }

        public int getAdded() { return added; }
        public int getUpdated() { return updated; }
        public int getSkipped() { return skipped; }
        public List<String> getErrors() { return errors; }
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<Map<String, String>> getExportRows(String type) {
        String normalizedType = type == null ? "STUDENT" : type.trim().toUpperCase();
        if ("ADVISER".equals(normalizedType)) {
            return getAdviserExportRows();
        }
        return getStudentExportRows();
    }

    private List<Map<String, String>> getStudentExportRows() {
        List<Map<String, String>> rows = new ArrayList<>();
        List<Student> students = studentRepository.findAll();
        
        // First pass: collect all unique questionnaires that have evaluations (answered)
        java.util.Set<Long> questionnaireIds = new java.util.LinkedHashSet<>();
        java.util.Map<Long, Questionnaire> questionnaireMap = new java.util.LinkedHashMap<>();
        
        List<Evaluation> allEvaluations = evaluationRepository.findAll();
        for (Evaluation evaluation : allEvaluations) {
            if (evaluation.getQuestionnaire() != null) {
                questionnaireIds.add(evaluation.getQuestionnaire().getId());
                questionnaireMap.put(evaluation.getQuestionnaire().getId(), evaluation.getQuestionnaire());
            }
        }
        
        // Sort questionnaires by ID for consistent ordering
        List<Questionnaire> sortedQuestionnaires = new ArrayList<>(questionnaireMap.values());
        sortedQuestionnaires.sort((q1, q2) -> q1.getId().compareTo(q2.getId()));

        for (Student student : students) {
            Map<String, String> row = new LinkedHashMap<>();

            String className = "";
            if (student.getClasses() != null && !student.getClasses().isEmpty()) {
                className = student.getClasses().get(0).getName() == null ? "" : student.getClasses().get(0).getName();
            }

            String teamCode = "";
            String memberNumber = "";
            String adviserEmail = "";
            Team studentTeam = null;
            List<TeamStudent> memberships = teamStudentRepository.findByStudentId(student.getId());
            if (!memberships.isEmpty()) {
                TeamStudent membership = memberships.get(0);
                if (membership.getTeam() != null) {
                    studentTeam = membership.getTeam();
                    teamCode = studentTeam.getName() == null ? "" : studentTeam.getName();
                    if (studentTeam.getAdvisers() != null && !studentTeam.getAdvisers().isEmpty()) {
                        User adviser = studentTeam.getAdvisers().get(0);
                        adviserEmail = adviser.getEmail() == null ? "" : adviser.getEmail();
                    }
                }
                memberNumber = membership.getPosition() == null ? "" : String.valueOf(membership.getPosition());
            }

            row.put("CLASS", className);
            row.put("TEAMCODE", teamCode);
            row.put("MEMBER#", memberNumber);
            row.put("STUDENTID", student.getStudentId() == null ? "" : student.getStudentId());
            row.put("LASTNAME", student.getLastName() == null ? "" : student.getLastName());
            row.put("FIRSTNAME", student.getFirstName() == null ? "" : student.getFirstName());
            row.put("EMAIL", student.getEmail() == null ? "" : student.getEmail());
            row.put("ADVISOREMAIL", adviserEmail);
            
            // Add questionnaire scores for this student's team
            if (studentTeam != null) {
                for (Questionnaire questionnaire : sortedQuestionnaires) {
                    String scoreValue = "";
                    
                    // Find evaluation for this team and questionnaire
                    List<Evaluation> teamEvaluations = evaluationRepository.findByTeamId(studentTeam.getId());
                    for (Evaluation evaluation : teamEvaluations) {
                        if (evaluation.getQuestionnaire() != null && 
                            evaluation.getQuestionnaire().getId().equals(questionnaire.getId())) {
                            
                            if (evaluation.getScores() != null && !evaluation.getScores().isEmpty()) {
                                // Calculate total score from numeric scores
                                double totalScore = 0;
                                int numericScoreCount = 0;
                                
                                for (group9.advisor_eval_system.entity.EvaluationScore score : evaluation.getScores()) {
                                    if (score.getNumericScore() != null) {
                                        totalScore += score.getNumericScore();
                                        numericScoreCount++;
                                    }
                                }
                                
                                if (numericScoreCount > 0) {
                                    // Calculate average
                                    double average = totalScore / numericScoreCount;
                                    scoreValue = String.format("%.2f", average);
                                } else {
                                    // If no numeric scores, just mark as answered
                                    scoreValue = "Answered";
                                }
                            } else {
                                // Evaluation exists but no scores yet
                                scoreValue = "In Progress";
                            }
                            break; // Use first evaluation for this team+questionnaire
                        }
                    }
                    
                    row.put(questionnaire.getTitle(), scoreValue);
                }
            }
            
            rows.add(row);
        }

        return rows;
    }

    private List<Map<String, String>> getAdviserExportRows() {
        List<Map<String, String>> rows = new ArrayList<>();
        List<User> advisers = userRepository.findByRole(User.UserRole.ADVISER);

        for (User adviser : advisers) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("LASTNAME", adviser.getLastName() == null ? "" : adviser.getLastName());
            row.put("FIRSTNAME", adviser.getFirstName() == null ? "" : adviser.getFirstName());
            row.put("EMAIL", adviser.getEmail() == null ? "" : adviser.getEmail());
            row.put("ADVISOREMAIL", adviser.getEmail() == null ? "" : adviser.getEmail());
            rows.add(row);
        }

        return rows;
    }

    @Transactional
    public void deleteUser(Long id) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            User.UserRole role = user.getRole();

            if (role == User.UserRole.ADVISER) {
                // Delete evaluation scores for evaluations by this adviser
                entityManager.createNativeQuery("DELETE FROM evaluation_scores WHERE evaluation_id IN (SELECT id FROM evaluations WHERE adviser_id = ?1)")
                        .setParameter(1, id)
                        .executeUpdate();
                
                // Delete evaluations conducted by this adviser
                entityManager.createNativeQuery("DELETE FROM evaluations WHERE adviser_id = ?1")
                        .setParameter(1, id)
                        .executeUpdate();
                
                // Remove adviser from teams
                entityManager.createNativeQuery("DELETE FROM team_advisers WHERE adviser_id = ?1")
                        .setParameter(1, id)
                        .executeUpdate();
            }
            
            if (role == User.UserRole.TEACHER) {
                // Get all classes for this teacher
                List<Long> classIds = entityManager
                    .createNativeQuery("SELECT id FROM classes WHERE teacher_id = ?1", Long.class)
                    .setParameter(1, id)
                    .getResultList();
                
                if (!classIds.isEmpty()) {
                    String classIdList = classIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
                    
                    // Delete evaluation_scores for teams in these classes
                    entityManager.createNativeQuery(
                        "DELETE FROM evaluation_scores WHERE evaluation_id IN (SELECT id FROM evaluations WHERE team_id IN (SELECT id FROM teams WHERE class_id IN (" + classIdList + ")))")
                        .executeUpdate();
                    
                    // Delete evaluations for teams in these classes
                    entityManager.createNativeQuery(
                        "DELETE FROM evaluations WHERE team_id IN (SELECT id FROM teams WHERE class_id IN (" + classIdList + "))")
                        .executeUpdate();
                    
                    // Delete questionnaire assignments for teams in these classes
                    entityManager.createNativeQuery(
                        "DELETE FROM team_questionnaires WHERE team_id IN (SELECT id FROM teams WHERE class_id IN (" + classIdList + "))")
                        .executeUpdate();
                    
                    // Delete teams in these classes
                    entityManager.createNativeQuery(
                        "DELETE FROM teams WHERE class_id IN (" + classIdList + ")")
                        .executeUpdate();
                }
                
                // Delete questionnaire items
                entityManager.createNativeQuery("DELETE FROM questionnaire_items WHERE questionnaire_id IN (SELECT id FROM questionnaires WHERE created_by_teacher_id = ?1)")
                        .setParameter(1, id)
                        .executeUpdate();
                
                // Delete class questionnaire assignments
                entityManager.createNativeQuery("DELETE FROM class_questionnaires WHERE questionnaire_id IN (SELECT id FROM questionnaires WHERE created_by_teacher_id = ?1)")
                        .setParameter(1, id)
                        .executeUpdate();
                
                // Delete team questionnaire assignments
                entityManager.createNativeQuery("DELETE FROM team_questionnaires WHERE questionnaire_id IN (SELECT id FROM questionnaires WHERE created_by_teacher_id = ?1)")
                        .setParameter(1, id)
                        .executeUpdate();
                
                // Delete questionnaires created by this teacher
                entityManager.createNativeQuery("DELETE FROM questionnaires WHERE created_by_teacher_id = ?1")
                        .setParameter(1, id)
                        .executeUpdate();
                
                // Delete classes
                entityManager.createNativeQuery("DELETE FROM classes WHERE teacher_id = ?1")
                        .setParameter(1, id)
                        .executeUpdate();
            }
            
            if (role == User.UserRole.STUDENT) {
                // Delete student from teams
                entityManager.createNativeQuery("DELETE FROM student_teams WHERE student_id IN (SELECT id FROM students WHERE created_by = ?1 OR email = (SELECT email FROM users WHERE id = ?1))")
                        .setParameter(1, id)
                        .executeUpdate();
                
                // Delete student from classes
                entityManager.createNativeQuery("DELETE FROM student_classes WHERE student_id IN (SELECT id FROM students WHERE created_by = ?1 OR email = (SELECT email FROM users WHERE id = ?1))")
                        .setParameter(1, id)
                        .executeUpdate();
                
                // Delete the Student entity
                entityManager.createNativeQuery("DELETE FROM students WHERE created_by = ?1 OR email = (SELECT email FROM users WHERE id = ?1)")
                        .setParameter(1, id)
                        .executeUpdate();
            }
            
            // Delete reports generated by this user
            entityManager.createNativeQuery("DELETE FROM reports WHERE generated_by = ?1")
                    .setParameter(1, id)
                    .executeUpdate();
            
            // Finally, delete the user
            entityManager.createNativeQuery("DELETE FROM users WHERE id = ?1")
                    .setParameter(1, id)
                    .executeUpdate();
            
        } catch (Exception e) {
            logger.error("Error deleting user with ID: " + id, e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new RuntimeException("Failed to delete user: " + errorMsg, e);
        }
    }

    /**
     * Upload users from CSV/Excel file
     * Expected columns: Email, FirstName, LastName, Role, PhoneNumber(optional), Department(optional)
     */
    public UploadResult uploadUserSheet(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        List<String[]> rows;

        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            rows = parseCsv(file);
        } else {
            rows = parseExcel(file);
        }

        int added = 0, updated = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length < 4) {
                errors.add("Row " + (i + 2) + ": Not enough columns (need Email, FirstName, LastName, Role)");
                skipped++;
                continue;
            }

            String email = row[0].trim().toLowerCase();
            String firstName = row[1].trim();
            String lastName = row[2].trim();
            String roleStr = row[3].trim().toUpperCase();

            if (email.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
                errors.add("Row " + (i + 2) + ": Email, FirstName, and LastName are required");
                skipped++;
                continue;
            }

            // Parse role
            User.UserRole role;
            try {
                role = User.UserRole.valueOf(roleStr);
            } catch (IllegalArgumentException e) {
                errors.add("Row " + (i + 2) + ": Unknown role '" + roleStr + "' for email " + email
                        + " (valid: TEACHER, ADVISER, STUDENT)");
                skipped++;
                continue;
            }

            // Don't allow modifying the system teacher admin via upload
            if (email.equals("authortet@gmail.com")) {
                skipped++;
                continue;
            }

            if (userRepository.existsByEmail(email)) {
                // Update existing user
                User existing = userRepository.findByEmail(email).get();
                existing.setFirstName(firstName);
                existing.setLastName(lastName);
                existing.setRole(role);
                userRepository.save(existing);
                updated++;
            } else {
                // Create new user
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setFirstName(firstName);
                newUser.setLastName(lastName);
                newUser.setRole(role);
                newUser.setIsActive(true);
                userRepository.save(newUser);
                added++;
            }
        }

        return new UploadResult(added, updated, skipped, errors);
    }

    /**
     * Upload advisers from CSV/Excel file
     * Expected columns: EMAIL, LASTNAME, FIRSTNAME (in any order)
     */
    public UploadResult uploadAdviserSheet(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        List<String[]> rows;

        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            rows = parseCsv(file);
        } else {
            rows = parseExcel(file);
        }

        int added = 0, updated = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        if (rows.isEmpty()) {
            errors.add("File is empty");
            return new UploadResult(added, updated, skipped, errors);
        }

        // Find column indices from header row
        String[] headers = rows.get(0);
        int emailIdx = -1, lastNameIdx = -1, firstNameIdx = -1;
        
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toUpperCase();
            if (header.contains("EMAIL")) emailIdx = i;
            else if (header.contains("LASTNAME") || header.contains("LAST")) lastNameIdx = i;
            else if (header.contains("FIRSTNAME") || header.contains("FIRST")) firstNameIdx = i;
        }

        if (emailIdx == -1 || lastNameIdx == -1 || firstNameIdx == -1) {
            errors.add("Header row must contain EMAIL, LASTNAME, and FIRSTNAME columns");
            return new UploadResult(0, 0, 0, errors);
        }

        // Process data rows (skip header row at index 0)
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            
            // Skip completely empty rows
            boolean isEmpty = true;
            for (String cell : row) {
                if (!cell.trim().isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
            if (isEmpty) continue;

            String email = (emailIdx < row.length) ? row[emailIdx].trim().toLowerCase() : "";
            String lastName = (lastNameIdx < row.length) ? row[lastNameIdx].trim() : "";
            String firstName = (firstNameIdx < row.length) ? row[firstNameIdx].trim() : "";

            if (lastName.isEmpty() || firstName.isEmpty() || email.isEmpty()) {
                errors.add("Row " + (i + 1) + ": EMAIL, LASTNAME, and FIRSTNAME are required");
                skipped++;
                continue;
            }

            // Don't allow modifying the system teacher admin via upload
            if (email.equals("authortet@gmail.com")) {
                skipped++;
                continue;
            }

            if (userRepository.existsByEmail(email)) {
                // Update existing adviser
                User existing = userRepository.findByEmail(email).get();
                existing.setFirstName(firstName);
                existing.setLastName(lastName);
                existing.setRole(User.UserRole.ADVISER);
                userRepository.save(existing);
                updated++;
            } else {
                // Create new adviser
                User newAdviser = new User();
                newAdviser.setEmail(email);
                newAdviser.setFirstName(firstName);
                newAdviser.setLastName(lastName);
                newAdviser.setRole(User.UserRole.ADVISER);
                newAdviser.setIsActive(true);
                userRepository.save(newAdviser);
                added++;
            }
        }

        return new UploadResult(added, updated, skipped, errors);
    }

    /**
     * Upload students from CSV/Excel file
     * Expected columns: CLASS, TEAMCODE, MEMBER#, STUDENTID, LASTNAME, FIRSTNAME, EMAIL, ADVISOREMAIL (in any order)
     */
    public UploadResult uploadStudentSheet(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        List<String[]> rows;

        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            rows = parseCsv(file);
        } else {
            rows = parseExcel(file);
        }

        int added = 0, updated = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        if (rows.isEmpty()) {
            errors.add("File is empty");
            return new UploadResult(added, updated, skipped, errors);
        }

        // Find column indices from header row
        String[] headers = rows.get(0);
        int classIdx = -1, teamIdx = -1, memberIdx = -1, studentIdIdx = -1, 
            lastNameIdx = -1, firstNameIdx = -1, emailIdx = -1, adviserEmailIdx = -1;
        
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toUpperCase();
            if (header.contains("CLASS")) classIdx = i;
            else if (header.contains("TEAMCODE") || header.contains("TEAM")) teamIdx = i;
            else if (header.contains("MEMBER")) memberIdx = i;
            else if (header.contains("STUDENTID") || header.contains("STUDENT")) studentIdIdx = i;
            else if (header.contains("LASTNAME") || header.contains("LAST")) lastNameIdx = i;
            else if (header.contains("FIRSTNAME") || header.contains("FIRST")) firstNameIdx = i;
            else if (header.contains("ADVISOREMAIL") || header.contains("ADVISER.*EMAIL")) adviserEmailIdx = i;
            else if (header.contains("EMAIL")) emailIdx = i;
        }

        if (classIdx == -1 || teamIdx == -1 || studentIdIdx == -1 || lastNameIdx == -1 || 
            firstNameIdx == -1 || emailIdx == -1 || adviserEmailIdx == -1) {
            errors.add("Header row must contain CLASS, TEAMCODE, STUDENTID, LASTNAME, FIRSTNAME, EMAIL, and ADVISOREMAIL columns");
            return new UploadResult(0, 0, 0, errors);
        }

        // Process data rows (skip header row at index 0)
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            
            // Skip completely empty rows
            boolean isEmpty = true;
            for (String cell : row) {
                if (!cell.trim().isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
            if (isEmpty) continue;

            String className = (classIdx < row.length) ? row[classIdx].trim() : "";
            String teamCode = (teamIdx < row.length) ? row[teamIdx].trim() : "";
            String memberNum = (memberIdx < row.length) ? row[memberIdx].trim() : "";
            String studentId = (studentIdIdx < row.length) ? row[studentIdIdx].trim() : "";
            String lastName = (lastNameIdx < row.length) ? row[lastNameIdx].trim() : "";
            String firstName = (firstNameIdx < row.length) ? row[firstNameIdx].trim() : "";
            String email = (emailIdx < row.length) ? row[emailIdx].trim().toLowerCase() : "";
            String adviserEmail = (adviserEmailIdx < row.length) ? row[adviserEmailIdx].trim().toLowerCase() : "";

            if (className.isEmpty() || studentId.isEmpty() || lastName.isEmpty() || firstName.isEmpty() || email.isEmpty() || adviserEmail.isEmpty()) {
                errors.add("Row " + (i + 1) + ": CLASS, STUDENTID, LASTNAME, FIRSTNAME, EMAIL, and ADVISOREMAIL are required");
                skipped++;
                continue;
            }

            // Validate adviser exists (by email)
            Optional<User> adviserOpt = userRepository.findByEmail(adviserEmail);
            if (!adviserOpt.isPresent()) {
                errors.add("Row " + (i + 1) + ": Adviser with email '" + adviserEmail + "' not found in system");
                skipped++;
                continue;
            }
            User adviser = adviserOpt.get();

            try {
                // Get or create class (available to all teachers)
                SchoolClass schoolClass = getOrCreateClass(className);

                // Get or create team
                Team team = getOrCreateTeam(teamCode, schoolClass);

                // Add adviser to team if not already assigned
                if (!team.getAdvisers().contains(adviser)) {
                    team.getAdvisers().add(adviser);
                    teamRepository.save(team);
                }

                // Create or update student user AND student entity
                User studentUser;
                if (userRepository.existsByEmail(email)) {
                    studentUser = userRepository.findByEmail(email).get();
                    studentUser.setFirstName(firstName);
                    studentUser.setLastName(lastName);
                    studentUser.setRole(User.UserRole.STUDENT);
                    userRepository.save(studentUser);
                    updated++;
                } else {
                    studentUser = new User();
                    studentUser.setEmail(email);
                    studentUser.setFirstName(firstName);
                    studentUser.setLastName(lastName);
                    studentUser.setRole(User.UserRole.STUDENT);
                    studentUser.setIsActive(true);
                    userRepository.save(studentUser);
                    added++;
                }
                
                // Check if Student entity already exists by studentId (preferred)
                // or fallback to email search
                Student student = null;
                
                // First try to find by studentId
                java.util.Optional<Student> existingByStudentId = studentRepository.findByStudentId(studentId);
                if (existingByStudentId.isPresent()) {
                    student = existingByStudentId.get();
                } else {
                    // Fallback: search by email for any orphaned records
                    java.util.List<Student> existingByEmail = studentRepository.findAll().stream()
                            .filter(s -> email.equalsIgnoreCase(s.getEmail() != null ? s.getEmail() : ""))
                            .toList();
                    if (!existingByEmail.isEmpty()) {
                        student = existingByEmail.get(0);
                        // Delete any other orphaned duplicates with same email
                        for (int j = 1; j < existingByEmail.size(); j++) {
                            studentRepository.delete(existingByEmail.get(j));
                        }
                    }
                }
                
                // Parse position/member number
                Integer position = null;
                try {
                    if (!memberNum.isEmpty()) {
                        position = Integer.parseInt(memberNum);
                    }
                } catch (NumberFormatException e) {
                    errors.add("Row " + (i + 2) + ": MEMBER# must be a number, got '" + memberNum + "'");
                    skipped++;
                    continue;
                }
                
                if (student != null) {
                    // Update existing Student entity
                    student.setStudentId(studentId);
                    student.setFirstName(firstName);
                    student.setLastName(lastName);
                    student.setEmail(email);
                    student.setCreatedBy(studentUser.getId());
                } else {
                    // Create new Student entity
                    student = new Student();
                    student.setStudentId(studentId);
                    student.setFirstName(firstName);
                    student.setLastName(lastName);
                    student.setEmail(email);
                    student.setCreatedBy(studentUser.getId());
                }
                studentRepository.save(student);
                
                // Clear and re-add classes
                student.getClasses().clear();
                student.getClasses().add(schoolClass);
                studentRepository.save(student);
                
                // Handle team membership with position via TeamStudent join entity
                // Delete old TeamStudent records for this student and team
                teamStudentRepository.deleteByStudentIdAndTeamId(student.getId(), team.getId());
                
                // Create new TeamStudent with position
                TeamStudent teamStudent = new TeamStudent();
                teamStudent.setStudent(student);
                teamStudent.setTeam(team);
                teamStudent.setPosition(position);
                teamStudentRepository.save(teamStudent);
            } catch (Exception e) {
                errors.add("Row " + (i + 2) + ": Error processing student: " + e.getMessage());
                skipped++;
            }
        }

        return new UploadResult(added, updated, skipped, errors);
    }

    /**
     * Get existing class or create new one (available to all teachers)
     */
    private SchoolClass getOrCreateClass(String className) {
        // Try to find class by name (not restricted by teacher)
        // Get all classes and search by name
        List<SchoolClass> allClasses = schoolClassRepository.findAll();
        for (SchoolClass c : allClasses) {
            if (c.getName().equalsIgnoreCase(className)) {
                return c;
            }
        }

        // Create new class if not found
        SchoolClass newClass = new SchoolClass();
        newClass.setName(className);
        // Use first available teacher or create without specific teacher assignment
        List<User> teachers = userRepository.findByRole(User.UserRole.TEACHER);
        if (!teachers.isEmpty()) {
            newClass.setTeacher(teachers.get(0));
        }
        newClass.setSchoolYear("2024-2025"); // Default school year
        newClass.setIsActive(true);
        return schoolClassRepository.save(newClass);
    }

    /**
     * Get existing team or create new one
     */
    private Team getOrCreateTeam(String teamCode, SchoolClass schoolClass) {
        // Try to find team with this code in the class
        List<Team> teams = teamRepository.findBySchoolClassId(schoolClass.getId());
        for (Team t : teams) {
            if (t.getName().equalsIgnoreCase(teamCode)) {
                return t;
            }
        }

        // Create new team if not found
        Team newTeam = new Team();
        newTeam.setName(teamCode);
        newTeam.setSchoolClass(schoolClass);
        newTeam.setIsActive(true);
        return teamRepository.save(newTeam);
    }

    private List<String[]> parseCsv(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(",", -1);
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = cols[i].trim().replaceAll("^\"|\"$", "");
                }
                // Skip header row
                if (firstLine) {
                    firstLine = false;
                    String first = cols[0].toUpperCase();
                    if (first.contains("EMAIL") || first.contains("NAME") || first.contains("USER")) continue;
                }
                rows.add(cols);
            }
        }
        return rows;
    }

    private List<String[]> parseExcel(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                
                // Dynamically read all columns in the row
                int lastCellNum = row.getLastCellNum();
                String[] cols = new String[lastCellNum];
                for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
                    cols[cellIndex] = getCellString(row.getCell(cellIndex));
                }
                
                // Skip empty rows
                boolean isEmpty = true;
                for (String col : cols) {
                    if (!col.isEmpty()) {
                        isEmpty = false;
                        break;
                    }
                }
                if (isEmpty) continue;
                
                rows.add(cols);
            }
        }
        return rows;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            default: return "";
        }
    }
}
