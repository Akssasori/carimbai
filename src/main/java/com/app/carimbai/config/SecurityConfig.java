package com.app.carimbai.config;

import com.app.carimbai.repositories.CustomerRepository;
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
    private final CustomerRepository customerRepository;

    public SecurityConfig(JwtService jwtService,
                          StaffUserRepository staffUserRepository,
                          CustomerRepository customerRepository) {
        this.jwtService = jwtService;
        this.staffUserRepository = staffUserRepository;
        this.customerRepository = customerRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, staffUserRepository, customerRepository);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/customers/login-or-register",
                                "/api/customers/social-login",
                                "/api/staff/login",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/error"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/merchants/*/programs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/notifications/vapid-public-key").permitAll()

                        // Rotas do cliente final: exigem token de cliente.
                        .requestMatchers(HttpMethod.GET, "/api/cards/customer/**").hasAuthority("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/cards/*/redeem-qr").hasAuthority("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/qr/**").hasAuthority("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/notifications/subscribe").hasAuthority("CUSTOMER")

                        // Inscrição de cliente em programa pelo staff.
                        .requestMatchers(HttpMethod.POST, "/api/cards").hasAnyAuthority("CASHIER", "ADMIN")

                        .requestMatchers(
                                "/api/auth/switch-merchant",
                                "/api/stamp/**",
                                "/api/redeem/**",
                                "/api/admin/**",
                                "/api/merchants/**",
                                "/api/staff-users/**",
                                "/api/staff/dashboard/**",
                                "/api/staff/stamps/**",
                                "/api/staff/rewards/**"
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
