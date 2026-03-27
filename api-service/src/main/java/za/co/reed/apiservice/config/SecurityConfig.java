package za.co.reed.apiservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import za.co.reed.apiservice.security.JwtAuthFilter;
import za.co.reed.apiservice.security.RateLimitFilter;

/**
 * Spring Security configuration — stateless JWT bearer auth.
 *
 * All /api/v1/** endpoints require a valid JWT.
 * Actuator health endpoint is publicly accessible (for load balancer health checks).
 * Swagger UI is accessible in dev profile only (controlled via springdoc properties).
 *
 * Filter order:
 *   1. RateLimitFilter  — reject over-limit requests before JWT processing
 *   2. JwtAuthFilter    — validate token and set SecurityContext
 *   3. Spring Security  — enforce @PreAuthorize and path-level rules
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Stateless — no session, no CSRF needed
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)

                // Public endpoints
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                )

                // Custom filters — run before Spring's UsernamePasswordAuthenticationFilter
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter,  UsernamePasswordAuthenticationFilter.class)

                .build();
    }
}
