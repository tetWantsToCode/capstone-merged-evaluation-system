package group9.advisor_eval_system.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import group9.advisor_eval_system.dto.AuthResponse;
import group9.advisor_eval_system.entity.User;
import group9.advisor_eval_system.repository.UserRepository;
import group9.advisor_eval_system.util.JwtTokenProvider;
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
public class GoogleLoginService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${google.client.id}")
    private String googleClientId;

    @Value("${google.client.secret}")
    private String googleClientSecret;

    @Value("${google.redirect.uri}")
    private String redirectUri;

    private static final List<String> SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/forms",
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/spreadsheets",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile"
    );

    public GoogleLoginService(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Generate Google OAuth authorization URL for login
     */
    public String generateAuthorizationUrl() {
        try {
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    googleClientId,
                    googleClientSecret,
                    SCOPES
            ).setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            return flow.newAuthorizationUrl()
                    .setRedirectUri(redirectUri)
                    .build();
        } catch (Exception e) {
            log.error("Error generating authorization URL", e);
            throw new RuntimeException("Failed to generate authorization URL", e);
        }
    }

    /**
     * Login with OAuth authorization code (gets full API access tokens)
     */
    @Transactional
    public AuthResponse loginWithAuthorizationCode(String authorizationCode) {
        try {
            // Exchange authorization code for tokens
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    googleClientId,
                    googleClientSecret,
                    authorizationCode,
                    redirectUri
            ).execute();

            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken();
            Long expiresInSeconds = tokenResponse.getExpiresInSeconds();

            // Get user info from Google
            Map<String, Object> userInfo = getUserInfo(accessToken);
            String googleEmail = (String) userInfo.get("email");
            Boolean emailVerified = (Boolean) userInfo.get("verified_email");
            String googleId = (String) userInfo.get("id");
            String givenName = (String) userInfo.get("given_name");
            String familyName = (String) userInfo.get("family_name");

            if (googleEmail == null || googleEmail.isBlank()) {
                throw new RuntimeException("Google email not found");
            }
            if (emailVerified == null || !emailVerified) {
                throw new RuntimeException("Google email is not verified");
            }

            final String email = googleEmail.toLowerCase().trim();

            // Check if user exists in system
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException(
                            "This email is not authorized to access the system. Please contact your administrator."
                    ));

            // Check if user is active
            if (user.getIsActive() == null || !user.getIsActive()) {
                throw new RuntimeException("Account is inactive");
            }

            // Update user with Google info and tokens
            if (user.getFirstName() == null || user.getFirstName().isBlank() ||
                    user.getFirstName().equals("Pending") || user.getFirstName().equals("To Be Provided")) {
                if (givenName != null && !givenName.isBlank()) {
                    user.setFirstName(givenName);
                }
            }

            if (user.getLastName() == null || user.getLastName().isBlank() ||
                    user.getLastName().equals("Pending") || user.getLastName().equals("Login")) {
                if (familyName != null && !familyName.isBlank()) {
                    user.setLastName(familyName);
                }
            }

            // Store Google OAuth tokens for API access
            user.setGoogleId(googleId);
            user.setGoogleEmail(email);
            user.setGoogleAccessToken(accessToken);
            user.setGoogleRefreshToken(refreshToken);
            user.setGoogleTokenExpiry(LocalDateTime.now().plusSeconds(expiresInSeconds));
            user.setIsGoogleLinked(true);

            userRepository.save(user);

            log.info("User {} logged in successfully with full OAuth access", email);

            // Issue JWT token
            String token = jwtTokenProvider.generateToken(user.getEmail(), user.getId(), user.getRole().toString());

            return new AuthResponse(user, token, "Login successful");

        } catch (Exception e) {
            log.error("Error during OAuth login", e);
            throw new RuntimeException("Failed to login with Google: " + e.getMessage(), e);
        }
    }

    /**
     * Get user info from Google using access token
     */
    private Map<String, Object> getUserInfo(String accessToken) {
        try {
            HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
            GenericUrl url = new GenericUrl("https://www.googleapis.com/oauth2/v2/userinfo");
            HttpRequest request = requestFactory.buildGetRequest(url);
            request.getHeaders().setAuthorization("Bearer " + accessToken);

            String response = request.execute().parseAsString();
            return GsonFactory.getDefaultInstance().fromString(response, Map.class);
        } catch (Exception e) {
            log.error("Error fetching user info from Google", e);
            throw new RuntimeException("Failed to fetch user info", e);
        }
    }
}