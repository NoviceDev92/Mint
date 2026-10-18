package com.mint.config;

import com.mint.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security Configuration.
 * Configures stateless JWT-based authentication and public vs. protected endpoints.
 */

//Spring Boot has a default security config called Filter Chain, we want to override it and make it custom
// @Configuration tells that this class will configure new Beans
// @EnableWebSecurity allows overriding with a custom security filter chain
// @EnableMethodSecurity helps to give methods special security features (annotations)
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Password encoder bean using standard BCrypt algorithm with automatic salting.
     */
    // since we cannot write annotations like @Service on Spring Boot library code,
    // we use @Bean to tell IoC to create instances
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Defines the HTTP security filter chain.
     */

    // CSRF -> using attached cookies to try to make malicious requests
    // but we dont use cookies, we use JWT directly in auth header, so no need for csrf

    // CORS -> allows JS to know which functionalities are allowed to interact across origins (different ports)
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF because our API is stateless and authenticates via Bearer tokens (not cookies)
                //stateless because JWT verification on every request, no HTTP session info stored
                .csrf(AbstractHttpConfigurer::disable)

                // Enable CORS with default permissive development settings
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Stateless sessions: server never stores session cookies in memory
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Endpoint authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints: registration, login, token refresh
                        .requestMatchers("/api/v1/auth/**").permitAll() // public access
                        // Public endpoints: swagger documentation and error dispatch
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll() // public access
                        // All other endpoints require a valid JWT
                        .anyRequest().authenticated() // no access public
                )

                // Insert our JWT filter before Spring's default username/password filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Permissive CORS setup for local testing and development.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Idempotency-Key"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
