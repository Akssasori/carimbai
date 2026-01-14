package com.app.carimbai.config;

import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.JwtAuthenticationFilter;
import com.app.carimbai.services.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity // se quiser usar @PreAuthorize
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
                        // Libera auth e docs
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/customers/login-or-register",
                                "/api/staff/login",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health"
                        ).permitAll()

                        // endpoints do cliente (por enquanto) liberados
                        .requestMatchers("/api/cards/**").permitAll()
                        .requestMatchers("/api/qr/**").permitAll()

                        // protege stamp/redeem/admin
                        .requestMatchers("/api/stamp/**", "/api/redeem/**", "/api/admin/**")
                        .authenticated()

                        .anyRequest().denyAll()
                )
                .httpBasic(Customizer.withDefaults());

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 🔹 Origem do Vercel + localhost dev
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
                "X-Location-Id"
        ));

        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(false); // você usa Bearer, não cookie

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
