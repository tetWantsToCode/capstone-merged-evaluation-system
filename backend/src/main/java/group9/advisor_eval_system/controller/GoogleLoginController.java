package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.dto.AuthResponse;
import group9.advisor_eval_system.dto.GoogleCallbackRequest;
import group9.advisor_eval_system.service.GoogleLoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
@Slf4j
public class GoogleLoginController {

    private final GoogleLoginService googleLoginService;

    /**
     * Get Google OAuth authorization URL for login
     */
    @GetMapping("/authorization-url")
    public ResponseEntity<?> getAuthorizationUrl() {
        try {
            String authUrl = googleLoginService.generateAuthorizationUrl();
            return ResponseEntity.ok(Map.of("authUrl", authUrl));
        } catch (Exception e) {
            log.error("Error generating authorization URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate authorization URL"));
        }
    }

    /**
     * Handle OAuth callback and complete login
     */
    @PostMapping("/callback")
    public ResponseEntity<?> handleCallback(@RequestBody GoogleCallbackRequest request) {
        try {
            if (request.getCode() == null || request.getCode().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("Missing authorization code"));
            }

            AuthResponse response = googleLoginService.loginWithAuthorizationCode(request.getCode());
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Login failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    public static class ErrorResponse {
        private String message;
        public ErrorResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}