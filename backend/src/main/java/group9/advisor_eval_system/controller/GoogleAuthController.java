package group9.advisor_eval_system.controller;

import group9.advisor_eval_system.dto.GoogleAuthUrlResponse;
import group9.advisor_eval_system.dto.GoogleCallbackRequest;
import group9.advisor_eval_system.dto.GoogleLinkStatusResponse;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.UserRepository;
import group9.advisor_eval_system.service.GoogleAuthService;
import group9.advisor_eval_system.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/google-auth")
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthController {

    private final GoogleAuthService googleAuthService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Get Google OAuth authorization URL
     */
    @GetMapping("/authorization-url")
    public ResponseEntity<?> getAuthorizationUrl(@RequestHeader("Authorization") String authHeader) {
        try {
            User user = getUserFromToken(authHeader);
            
            // Check if user is a TEACHER
            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Only teachers can link Google accounts"));
            }
            
            String authUrl = googleAuthService.generateAuthorizationUrl(user.getId());
            return ResponseEntity.ok(new GoogleAuthUrlResponse(authUrl));
        } catch (Exception e) {
            log.error("Error generating authorization URL", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Handle OAuth callback and link account
     */
    @PostMapping("/callback")
    public ResponseEntity<?> handleCallback(
            @RequestBody GoogleCallbackRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            User user = getUserFromToken(authHeader);
            
            // Check if user is a TEACHER
            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new GoogleLinkStatusResponse(false, null, "Only teachers can link Google accounts"));
            }
            
            googleAuthService.linkGoogleAccount(user.getId(), request.getCode());

            // Refresh user data
            user = userRepository.findById(user.getId()).orElseThrow();

            return ResponseEntity.ok(new GoogleLinkStatusResponse(
                    true,
                    user.getGoogleEmail(),
                    "Google account linked successfully"
            ));
        } catch (Exception e) {
            log.error("Error handling OAuth callback", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new GoogleLinkStatusResponse(
                            false,
                            null,
                            "Failed to link Google account: " + e.getMessage()
                    ));
        }
    }

    /**
     * Check Google account link status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getLinkStatus(@RequestHeader("Authorization") String authHeader) {
        try {
            User user = getUserFromToken(authHeader);
            
            // Check if user is a TEACHER
            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.ok(new GoogleLinkStatusResponse(
                        false,
                        null,
                        "Only teachers can link Google accounts"
                ));
            }

            return ResponseEntity.ok(new GoogleLinkStatusResponse(
                    user.getIsGoogleLinked(),
                    user.getGoogleEmail(),
                    user.getIsGoogleLinked() ? "Google account is linked" : "No Google account linked"
            ));
        } catch (Exception e) {
            log.error("Error checking link status", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * Unlink Google account
     */
    @DeleteMapping("/unlink")
    public ResponseEntity<?> unlinkAccount(@RequestHeader("Authorization") String authHeader) {
        try {
            User user = getUserFromToken(authHeader);
            
            // Check if user is a TEACHER
            if (user.getRole() != User.UserRole.TEACHER) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new GoogleLinkStatusResponse(false, null, "Only teachers can unlink Google accounts"));
            }
            
            googleAuthService.unlinkGoogleAccount(user.getId());

            return ResponseEntity.ok(new GoogleLinkStatusResponse(
                    false,
                    null,
                    "Google account unlinked successfully"
            ));
        } catch (Exception e) {
            log.error("Error unlinking account", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new GoogleLinkStatusResponse(
                            true,
                            null,
                            "Failed to unlink Google account: " + e.getMessage()
                    ));
        }
    }

    /**
     * Extract user from JWT token in Authorization header
     */
    private User getUserFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid or missing Authorization header");
        }
        
        String token = authHeader.substring(7);
        
        // Validate token
        if (!jwtTokenProvider.validateToken(token)) {
            throw new RuntimeException("Invalid or expired token");
        }
        
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
    
    // Inner class for error responses
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
}
