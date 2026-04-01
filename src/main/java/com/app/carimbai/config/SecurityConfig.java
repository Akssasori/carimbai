package com.app.carimbai.config;

import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.JwtAuthenticationFilter;
import com.app.carimbai.services.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final StaffUserRepository staffUserRepository;

    public SecurityConfig(JwtService jwtService, StaffUserRepository staffUserRepository) {
        this.jwtService = jwtService;
        this.staffUserRepository = staffUserRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, staffUserRepository);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/customers/login-or-register",
                                "/api/staff/login",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/error"
                        ).permitAll()

                        .requestMatchers("/api/cards/**").permitAll()
                        .requestMatchers("/api/qr/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/merchants/*/programs").permitAll()

                        .requestMatchers(
                                "/api/auth/switch-merchant",
                                "/api/stamp/**",
                                "/api/redeem/**",
                                "/api/admin/**",
                                "/api/merchants/**",
                                "/api/staff-users/**"
                        ).authenticated()

                        .anyRequest().denyAll()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "https://carimbai-app.vercel.app",
                "http://localhost:5173",
                "http://localhost:3000",
                "http://localhost:1234"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Idempotency-Key",
                "X-Location-Id",
                "X-Cashier-Pin",
                "X-Merchant-Id"
        ));

        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
