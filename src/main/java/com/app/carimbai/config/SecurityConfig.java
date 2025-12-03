package com.app.carimbai.config;

import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.JwtAuthenticationFilter;
import com.app.carimbai.services.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Libera auth e docs
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/customers/login-or-register",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health"
                        ).permitAll()
                        // endpoints do cliente ainda liberados (pode fechar depois)
                        .requestMatchers("/api/cards/**").permitAll()
                        // protege stamp/redeem/admin
                        .requestMatchers("/api/stamp/**", "/api/redeem/**", "/api/admin/**")
                        .authenticated()
                        // default: negar tudo que não foi configurado
                        .anyRequest().denyAll()
                )
                .httpBasic(Customizer.withDefaults());

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
