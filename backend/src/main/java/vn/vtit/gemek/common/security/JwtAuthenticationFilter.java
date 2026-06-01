/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT authentication filter that runs once per request.
 *
 * <p>Extracts the Bearer token from the {@code Authorization} header, validates it,
 * checks the Redis blocklist (to detect logged-out tokens), and populates
 * the Spring Security context with the authenticated {@link UserPrincipal}.
 *
 * <p>Public endpoints ({@code /api/auth/login} and {@code /api/auth/refresh}) are skipped.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** Redis key prefix for the JWT blocklist. */
    private static final String BLOCKLIST_KEY_PREFIX = "blocklist:";

    /** Authorization header name. */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer token prefix. */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Constructs the filter with its required dependencies.
     *
     * @param tokenProvider  JWT token utility.
     * @param userRepository user lookup for building the principal.
     * @param redisTemplate  Redis template for blocklist checks.
     */
    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   UserRepository userRepository,
                                   StringRedisTemplate redisTemplate) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Performs JWT validation and sets up the security context for each request.
     *
     * @param request     the HTTP request.
     * @param response    the HTTP response.
     * @param filterChain the remaining filter chain.
     * @throws ServletException if a servlet error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);

        // No token present — continue unauthenticated; security config will reject if protected.
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = tokenProvider.parseToken(token);

            // Ensure this is an access token, not a refresh token.
            if (!JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE))) {
                log.debug("Rejected non-access token type on {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            String jti = claims.getId();

            // Check blocklist — logout adds the JTI here with remaining TTL.
            Boolean isBlocked = redisTemplate.hasKey(BLOCKLIST_KEY_PREFIX + jti);
            if (Boolean.TRUE.equals(isBlocked)) {
                log.debug("Rejected blocklisted token JTI: {}", jti);
                filterChain.doFilter(request, response);
                return;
            }

            // Load user to verify account is still active.
            UUID userId = UUID.fromString(claims.getSubject());
            Optional<User> userOptional = userRepository.findById(userId);

            if (userOptional.isEmpty() || !userOptional.get().isActive()) {
                log.debug("Rejected token for inactive or missing user: {}", userId);
                filterChain.doFilter(request, response);
                return;
            }

            User user = userOptional.get();
            UserPrincipal principal = new UserPrincipal(user);

            // SECURITY-FIX: SEC-06 — use DB role instead of stale token claim to prevent privilege escalation
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.trace("Authenticated user {} on {}", userId, request.getRequestURI());

        } catch (JwtException ex) {
            // Invalid or expired token — do not populate context; let security config reject.
            log.debug("JWT parsing failed on {}: {}", request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the Bearer token from the Authorization header.
     *
     * @param request the HTTP request.
     * @return the raw JWT string, or {@code null} if absent or not a Bearer token.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String headerValue = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(headerValue) && headerValue.startsWith(BEARER_PREFIX)) {
            return headerValue.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
