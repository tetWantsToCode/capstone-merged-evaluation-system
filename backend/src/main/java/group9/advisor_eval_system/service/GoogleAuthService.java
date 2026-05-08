package group9.advisor_eval_system.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GoogleAuthService {

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Value("${google.redirect.uri}")
    private String redirectUri;

    private final UserRepository userRepository;

    private static final List<String> SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/forms",
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/spreadsheets",
            "https://www.googleapis.com/auth/userinfo.email");

    public GoogleAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Generate Google OAuth authorization URL
     */
    public String generateAuthorizationUrl(Long userId) {
        try {
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientId,
                    clientSecret,
                    SCOPES).setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            return flow.newAuthorizationUrl()
                    .setRedirectUri(redirectUri)
                    .setState(String.valueOf(userId))
                    .build();
        } catch (Exception e) {
            log.error("Error generating authorization URL", e);
            throw new RuntimeException("Failed to generate authorization URL", e);
        }
    }

    /**
     * Exchange authorization code for tokens and link to user account
     */
    @Transactional
    public void linkGoogleAccount(Long userId, String authorizationCode) {
        try {
            // Exchange code for tokens
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientId,
                    clientSecret,
                    authorizationCode,
                    redirectUri).execute();

            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken();
            Long expiresInSeconds = tokenResponse.getExpiresInSeconds();

            // Get user info
            String googleEmail = getUserEmail(accessToken);
            String googleId = getUserId(accessToken);

            // Update user entity
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setGoogleAccessToken(accessToken);
            // Google may omit refresh_token on subsequent grants.
            // Never overwrite an existing refresh token with null.
            if (refreshToken != null && !refreshToken.isBlank()) {
                user.setGoogleRefreshToken(refreshToken);
            }
            if (expiresInSeconds != null) {
                user.setGoogleTokenExpiry(LocalDateTime.now().plusSeconds(expiresInSeconds));
            }
            user.setGoogleEmail(googleEmail);
            user.setGoogleId(googleId);
            user.setIsGoogleLinked(true);

            userRepository.save(user);

            log.info("Successfully linked Google account for user {}", userId);

        } catch (Exception e) {
            log.error("Error linking Google account", e);
            throw new RuntimeException("Failed to link Google account", e);
        }
    }

    /**
     * Unlink Google account from user
     */
    @Transactional
    public void unlinkGoogleAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setGoogleAccessToken(null);
        user.setGoogleRefreshToken(null);
        user.setGoogleTokenExpiry(null);
        user.setGoogleEmail(null);
        user.setGoogleId(null);
        user.setIsGoogleLinked(false);

        userRepository.save(user);

        log.info("Successfully unlinked Google account for user {}", userId);
    }

    /**
     * Get valid access token (refresh if expired)
     */
    public String getValidAccessToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getIsGoogleLinked()) {
            throw new RuntimeException("Google account not linked");
        }

        if (user.getGoogleAccessToken() == null || user.getGoogleAccessToken().isBlank()) {
            throw new RuntimeException("Google access token missing; please unlink and link Google again");
        }

        // Check if token is expired or about to expire
        if (user.getGoogleTokenExpiry() == null
                || user.getGoogleTokenExpiry().isBefore(LocalDateTime.now().plusMinutes(5))) {
            refreshAccessToken(user);
        }

        return user.getGoogleAccessToken();
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    protected void refreshAccessToken(User user) {
        try {
            if (user.getGoogleRefreshToken() == null || user.getGoogleRefreshToken().isBlank()) {
                throw new RuntimeException("Google refresh token missing; please unlink and link Google again");
            }

            com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest refreshRequest = new com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    user.getGoogleRefreshToken(),
                    clientId,
                    clientSecret);

            GoogleTokenResponse tokenResponse = refreshRequest.execute();

            user.setGoogleAccessToken(tokenResponse.getAccessToken());
            if (tokenResponse.getExpiresInSeconds() != null) {
                user.setGoogleTokenExpiry(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresInSeconds()));
            }

            userRepository.save(user);

            log.info("Successfully refreshed access token for user {}", user.getId());

        } catch (Exception e) {
            log.error("Error refreshing access token", e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to refresh access token: " + e.getMessage(), e);
        }
    }

    /**
     * Get user email from Google using access token
     */
    private String getUserEmail(String accessToken) {
        Map<String, Object> userInfo = getUserInfo(accessToken);
        Object email = userInfo.get("email");
        return email != null ? String.valueOf(email) : null;
    }

    /**
     * Get user ID from Google using access token
     */
    private String getUserId(String accessToken) {
        Map<String, Object> userInfo = getUserInfo(accessToken);
        Object id = userInfo.get("id");
        if (id == null) {
            id = userInfo.get("sub");
        }
        return id != null ? String.valueOf(id) : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getUserInfo(String accessToken) {
        try {
            HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
            GenericUrl url = new GenericUrl("https://www.googleapis.com/oauth2/v2/userinfo");

            HttpRequest request = requestFactory.buildGetRequest(url);
            request.setHeaders(new HttpHeaders().setAuthorization("Bearer " + accessToken));

            HttpResponse response = request.execute();
            try (InputStreamReader reader = new InputStreamReader(response.getContent())) {
                return GsonFactory.getDefaultInstance().createJsonParser(reader).parse(Map.class);
            }
        } catch (Exception e) {
            log.error("Error fetching Google user info", e);
            throw new RuntimeException("Failed to fetch Google user info", e);
        }
    }
}
