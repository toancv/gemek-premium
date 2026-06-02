/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.vtit.gemek.common.security.JwtAuthenticationFilter;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 6 configuration.
 *
 * <p>Configures the stateless JWT filter chain, CORS, method security, and
 * the BCrypt password encoder. Public endpoints are explicitly whitelisted.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserRepository userRepository;

    // SECURITY-FIX: read active profile to conditionally block Swagger on prod
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String corsAllowedOrigins;

    /**
     * Constructs the security configuration with required dependencies.
     *
     * @param jwtAuthenticationFilter the JWT filter to insert before the standard auth filter.
     * @param userRepository          the user repository for loading user details.
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          UserRepository userRepository) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userRepository = userRepository;
    }

    /**
     * Configures the main security filter chain.
     *
     * <p>Key decisions:
     * <ul>
     *   <li>CSRF disabled — stateless API using JWT, no browser cookie sessions.</li>
     *   <li>Sessions set to STATELESS — no server-side session is ever created.</li>
     *   <li>JWT filter inserted before the standard username/password filter.</li>
     * </ul>
     *
     * @param http the {@link HttpSecurity} builder.
     * @return the configured {@link SecurityFilterChain}.
     * @throws Exception if an error occurs during configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Explicit CORS config — never rely on Spring's default pass-through.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF not needed for stateless JWT APIs.
                .csrf(AbstractHttpConfigurer::disable)
                // Stateless session — JWT is the sole auth mechanism.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // SECURITY-FIX: HTTP security headers — CSP, X-Content-Type-Options, HSTS, X-Frame-Options
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; script-src 'self'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data: blob:; connect-src 'self'")))
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        // Health check — always public.
                        // SECURITY-FIX: SEC-17 — /actuator/info removed; only health check is public
                        .requestMatchers("/actuator/health").permitAll()
                        // SECURITY-FIX: Swagger UI blocked on prod profile; permitted on dev/test only
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .access((authentication, context) -> {
                                    boolean isProd = activeProfile.contains("prod");
                                    return new AuthorizationDecision(!isProd);
                                })
                        // All other endpoints require authentication.
                        .anyRequest().authenticated())
                // Return 401 (not 403) for unauthenticated REST API requests.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (req, res, e) -> res.sendError(
                                jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
                // Insert JWT filter before the standard username/password filter.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Creates a {@link BCryptPasswordEncoder} with cost factor 12.
     *
     * <p>Cost factor 12 provides strong security with acceptable latency (~250ms per hash)
     * at the scale of this application.
     *
     * @return the configured password encoder.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Creates a {@link UserDetailsService} that loads users by email from the database.
     *
     * @return the {@link UserDetailsService} implementation.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)
                .map(vn.vtit.gemek.common.security.UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * Creates a {@link DaoAuthenticationProvider} wiring the user details service and encoder.
     *
     * @return the configured authentication provider.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Provides the CORS policy applied by the security filter chain.
     *
     * <p>Allowed origins are read from {@code cors.allowed-origins} (comma-separated list).
     * Credentials are supported; wildcard {@code "*"} is never used for credentialed requests.
     *
     * @return the {@link CorsConfigurationSource} bean.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(corsAllowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Exposes the {@link AuthenticationManager} bean for use in the auth service.
     *
     * @param authConfig Spring's authentication configuration.
     * @return the authentication manager.
     * @throws Exception if an error occurs during configuration.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
            throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
