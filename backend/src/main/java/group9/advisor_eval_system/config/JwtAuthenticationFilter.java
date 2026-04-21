package group9.advisor_eval_system.config;

import group9.advisor_eval_system.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            logger.info("JWT Filter - Request URI: " + request.getRequestURI());
            logger.info("JWT Filter - Token present: " + (jwt != null));

            if (StringUtils.hasText(jwt)) {
                logger.info("JWT Filter - Token value (first 20 chars): " + jwt.substring(0, Math.min(20, jwt.length())) + "...");
                boolean isValid = tokenProvider.validateToken(jwt);
                logger.info("JWT Filter - Token valid: " + isValid);
                
                if (isValid) {
                    Long userId = tokenProvider.getUserIdFromToken(jwt);
                    String role = tokenProvider.getRoleFromToken(jwt);
                    
                    logger.info("JWT Filter - User ID: " + userId + ", Role: " + role);

                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.singletonList(authority));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.info("JWT Filter - Authentication set successfully");
                } else {
                    logger.warn("JWT Filter - Token validation failed");
                }
            } else {
                logger.warn("JWT Filter - No token found in request");
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
