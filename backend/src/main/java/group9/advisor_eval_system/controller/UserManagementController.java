package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.service.UserManagementService;
import group9.advisor_eval_system.service.GoogleSheetsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-management")
public class UserManagementController {

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GoogleSheetsService googleSheetsService;

    @GetMapping("/users")
    public ResponseEntity<List<User>> getUsers() {
        return ResponseEntity.ok(userManagementService.getAllUsers());
    }

    @GetMapping("/export")
    public ResponseEntity<List<Map<String, String>>> getExportData(
            @RequestParam(name = "type", defaultValue = "STUDENT") String type) {
        return ResponseEntity.ok(userManagementService.getExportRows(type));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userManagementService.deleteUser(id);
            return ResponseEntity.ok(new MessageResponse("User removed successfully"));
        } catch (RuntimeException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponse(errorMsg));
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error: " + errorMsg));
        }
    }

    @PostMapping(value = "/upload-students", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadStudentSheet(@RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("File is empty"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null ||
                (!filename.toLowerCase().endsWith(".xlsx") &&
                        !filename.toLowerCase().endsWith(".xls") &&
                        !filename.toLowerCase().endsWith(".csv"))) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("File must be .xlsx, .xls, or .csv"));
        }

        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new MessageResponse("User not authenticated"));
            }

            Long userId = (Long) authentication.getPrincipal();
            User teacher = userManagementService.findById(userId);
            if (teacher == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new MessageResponse("User not found"));
            }

            String teacherEmail = teacher.getEmail();
            UserManagementService.UploadResult result = userManagementService.uploadStudentSheet(file, teacherEmail);
            String message = "Added: " + result.getAdded()
                    + ", Updated: " + result.getUpdated()
                    + ", Skipped: " + result.getSkipped();
            if (!result.getErrors().isEmpty()) {
                message += ". Errors: " + String.join("; ", result.getErrors());
            }
            return ResponseEntity.ok(new UploadResponse(message, result));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error reading file: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/upload-advisers", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadAdviserSheet(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("File is empty"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null ||
                (!filename.toLowerCase().endsWith(".xlsx") &&
                        !filename.toLowerCase().endsWith(".xls") &&
                        !filename.toLowerCase().endsWith(".csv"))) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("File must be .xlsx, .xls, or .csv"));
        }

        try {
            UserManagementService.UploadResult result = userManagementService.uploadAdviserSheet(file);
            String message = "Added: " + result.getAdded()
                    + ", Updated: " + result.getUpdated()
                    + ", Skipped: " + result.getSkipped();
            if (!result.getErrors().isEmpty()) {
                message += ". Errors: " + String.join("; ", result.getErrors());
            }
            return ResponseEntity.ok(new UploadResponse(message, result));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error reading file: " + e.getMessage()));
        }
    }

    @PostMapping("/google-sheets/url")
    public ResponseEntity<?> setGoogleSheetsUrl(
            @RequestBody GoogleSheetsUrlRequest request,
            Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new MessageResponse("User not authenticated"));
            }

            Long userId = (Long) authentication.getPrincipal();
            User teacher = userManagementService.findById(userId);
            if (teacher == null || !teacher.getRole().equals(User.UserRole.TEACHER)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Only teachers can configure Google Sheets"));
            }

            if (!teacher.getIsGoogleLinked()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new MessageResponse("Please link your Google account first"));
            }

            // Validate URL format only (not API accessibility)
            String sheetsUrl = request.getGoogleSheetsUrl();
            try {
                googleSheetsService.extractSheetIdFromUrl(sheetsUrl);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Invalid Google Sheets URL format. Use: https://docs.google.com/spreadsheets/d/{SHEET_ID}/edit..."));
            }

            teacher.setGoogleSheetsUrl(sheetsUrl);
            userManagementService.saveUser(teacher);

            return ResponseEntity.ok(new MessageResponse("Google Sheets URL saved successfully. Data will be validated when you push it to the sheet."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error saving Google Sheets URL: " + e.getMessage()));
        }
    }

    @GetMapping("/google-sheets/url")
    public ResponseEntity<?> getGoogleSheetsUrl(Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new MessageResponse("User not authenticated"));
            }

            Long userId = (Long) authentication.getPrincipal();
            User teacher = userManagementService.findById(userId);
            if (teacher == null || !teacher.getRole().equals(User.UserRole.TEACHER)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Only teachers can access Google Sheets settings"));
            }

            return ResponseEntity.ok(new GoogleSheetsUrlResponse(teacher.getGoogleSheetsUrl()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error retrieving Google Sheets URL: " + e.getMessage()));
        }
    }

    public static class MessageResponse {
        private String message;

        public MessageResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class UploadResponse {
        private String message;
        private UserManagementService.UploadResult result;

        public UploadResponse(String message, UserManagementService.UploadResult result) {
            this.message = message;
            this.result = result;
        }

        public String getMessage() {
            return message;
        }

        public UserManagementService.UploadResult getResult() {
            return result;
        }
    }

    public static class GoogleSheetsUrlRequest {
        private String googleSheetsUrl;

        public GoogleSheetsUrlRequest() {
        }

        public String getGoogleSheetsUrl() {
            return googleSheetsUrl;
        }

        public void setGoogleSheetsUrl(String googleSheetsUrl) {
            this.googleSheetsUrl = googleSheetsUrl;
        }
    }

    public static class GoogleSheetsUrlResponse {
        private String googleSheetsUrl;

        public GoogleSheetsUrlResponse(String googleSheetsUrl) {
            this.googleSheetsUrl = googleSheetsUrl;
        }

        public String getGoogleSheetsUrl() {
            return googleSheetsUrl;
        }
    }

    @PostMapping("/push-to-sheets")
    public ResponseEntity<?> pushDataToSheets(
            @RequestParam(name = "type", defaultValue = "STUDENT") String type,
            Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new MessageResponse("User not authenticated"));
            }

            Long userId = (Long) authentication.getPrincipal();
            User teacher = userManagementService.findById(userId);
            if (teacher == null || !teacher.getRole().equals(User.UserRole.TEACHER)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Only teachers can push data to Google Sheets"));
            }

            if (teacher.getGoogleSheetsUrl() == null || teacher.getGoogleSheetsUrl().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Google Sheets URL is not configured. Please set it up in settings."));
            }

            if (!teacher.getIsGoogleLinked()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Google account is not linked. Please link your Google account first."));
            }

            // Get the export data
            List<Map<String, String>> exportRows = userManagementService.getExportRows(type);
            
            if (exportRows.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("No data available to push to Google Sheets."));
            }

            // Get headers from first row
            Map<String, String> firstRow = exportRows.get(0);
            List<String> headers = new ArrayList<>(firstRow.keySet());
            
            // Convert rows to List<List<String>>
            List<List<String>> rows = new ArrayList<>();
            for (Map<String, String> row : exportRows) {
                List<String> rowData = new ArrayList<>();
                for (String header : headers) {
                    rowData.add(row.getOrDefault(header, ""));
                }
                rows.add(rowData);
            }

            // Push to Google Sheets
            if (type.equalsIgnoreCase("STUDENT")) {
                googleSheetsService.writeDataToSheet(teacher, headers, rows, "Adviser Evaluation Data");

                List<Map<String, String>> reportRows = userManagementService.getStudentReportsExportRows();
                if (!reportRows.isEmpty()) {
                    List<String> reportHeaders = new ArrayList<>();
                    for (Map<String, String> row : reportRows) {
                        for (String key : row.keySet()) {
                            if (!reportHeaders.contains(key)) {
                                reportHeaders.add(key);
                            }
                        }
                    }
                    
                    List<List<String>> reportData = new ArrayList<>();
                    for (Map<String, String> row : reportRows) {
                        List<String> rowData = new ArrayList<>();
                        for (String header : reportHeaders) {
                            rowData.add(row.getOrDefault(header, ""));
                        }
                        reportData.add(rowData);
                    }
                    googleSheetsService.writeDataToSheet(teacher, reportHeaders, reportData, "Student Reports");
                }
            } else {
                googleSheetsService.writeDataToSheet(teacher, headers, rows, "Advisers");
            }

            return ResponseEntity.ok(new MessageResponse("Data pushed to Google Sheets successfully. " + exportRows.size() + " rows updated."));
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Failed to push data to Google Sheets";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error pushing data: " + errorMsg));
        }
    }

    // ------------------------------------------------------------------
    // AI Key endpoints
    // ------------------------------------------------------------------

    @PostMapping("/ai-key")
    public ResponseEntity<?> setAiKey(
            @RequestBody AiKeyRequest request,
            Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new MessageResponse("User not authenticated"));
            }

            Long userId = (Long) authentication.getPrincipal();
            User teacher = userManagementService.findById(userId);
            if (teacher == null || !teacher.getRole().equals(User.UserRole.TEACHER)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Only teachers can configure AI keys"));
            }

            String provider = request.getAiProvider();
            if (provider == null
                    || !java.util.List.of("gemini", "openai", "anthropic").contains(provider.toLowerCase())) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Unsupported AI provider. Choose: gemini, openai, or anthropic"));
            }

            teacher.setAiApiKey(request.getAiApiKey());
            teacher.setAiProvider(provider.toLowerCase());
            userManagementService.saveUser(teacher);

            return ResponseEntity.ok(new MessageResponse("AI API key saved successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error saving AI key: " + e.getMessage()));
        }
    }

    @GetMapping("/ai-key")
    public ResponseEntity<?> getAiKey(Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new MessageResponse("User not authenticated"));
            }

            Long userId = (Long) authentication.getPrincipal();
            User teacher = userManagementService.findById(userId);
            if (teacher == null || !teacher.getRole().equals(User.UserRole.TEACHER)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Only teachers can access AI settings"));
            }

            boolean hasKey = teacher.getAiApiKey() != null && !teacher.getAiApiKey().isBlank();
            return ResponseEntity.ok(new AiKeyResponse(teacher.getAiProvider(), hasKey));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error retrieving AI key info: " + e.getMessage()));
        }
    }

    @DeleteMapping("/ai-key")
    public ResponseEntity<?> deleteAiKey(Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new MessageResponse("User not authenticated"));
            }

            Long userId = (Long) authentication.getPrincipal();
            User teacher = userManagementService.findById(userId);
            if (teacher == null || !teacher.getRole().equals(User.UserRole.TEACHER)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Only teachers can modify AI settings"));
            }

            teacher.setAiApiKey(null);
            teacher.setAiProvider(null);
            userManagementService.saveUser(teacher);

            return ResponseEntity.ok(new MessageResponse("AI API key removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error removing AI key: " + e.getMessage()));
        }
    }

    public static class AiKeyRequest {
        private String aiApiKey;
        private String aiProvider;

        public AiKeyRequest() {
        }

        public String getAiApiKey() {
            return aiApiKey;
        }

        public void setAiApiKey(String aiApiKey) {
            this.aiApiKey = aiApiKey;
        }

        public String getAiProvider() {
            return aiProvider;
        }

        public void setAiProvider(String aiProvider) {
            this.aiProvider = aiProvider;
        }
    }

    public static class AiKeyResponse {
        private String aiProvider;
        private boolean hasKey;

        public AiKeyResponse(String aiProvider, boolean hasKey) {
            this.aiProvider = aiProvider;
            this.hasKey = hasKey;
        }

        public String getAiProvider() {
            return aiProvider;
        }

        public boolean isHasKey() {
            return hasKey;
        }
    }
}
