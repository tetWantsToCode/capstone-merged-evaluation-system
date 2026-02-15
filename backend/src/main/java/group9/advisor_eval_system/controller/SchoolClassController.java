package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.entity.SchoolClass;
import group9.advisor_eval_system.service.SchoolClassService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes")
public class SchoolClassController {
    
    @Autowired
    private SchoolClassService schoolClassService;
    
    @GetMapping
    public ResponseEntity<?> getAllClasses() {
        try {
            List<SchoolClass> classes = schoolClassService.getAllClasses();
            System.out.println("Fetched " + classes.size() + " classes");
            return ResponseEntity.ok(classes);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error fetching classes: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Error fetching classes: " + e.getMessage()));
        }
    }
    
    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<List<SchoolClass>> getClassesByTeacher(@PathVariable Long teacherId) {
        List<SchoolClass> classes = schoolClassService.getClassesByTeacher(teacherId);
        return ResponseEntity.ok(classes);
    }
    
    @GetMapping("/active")
    public ResponseEntity<List<SchoolClass>> getActiveClasses() {
        List<SchoolClass> classes = schoolClassService.getActiveClasses();
        return ResponseEntity.ok(classes);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getClassById(@PathVariable Long id) {
        try {
            SchoolClass schoolClass = schoolClassService.getClassById(id);
            return ResponseEntity.ok(schoolClass);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }
    
    @PostMapping
    public ResponseEntity<?> createClass(@Valid @RequestBody SchoolClass schoolClass) {
        try {
            SchoolClass createdClass = schoolClassService.createClass(schoolClass);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdClass);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<?> updateClass(@PathVariable Long id, @Valid @RequestBody SchoolClass schoolClass) {
        try {
            SchoolClass updatedClass = schoolClassService.updateClass(id, schoolClass);
            return ResponseEntity.ok(updatedClass);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClass(@PathVariable Long id) {
        try {
            schoolClassService.deleteClass(id);
            return ResponseEntity.ok(new SuccessResponse("Class deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }
    
    // Inner classes for responses
    public static class ErrorResponse {
        private String message;
        
        public ErrorResponse(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
    
    public static class SuccessResponse {
        private String message;
        
        public SuccessResponse(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
