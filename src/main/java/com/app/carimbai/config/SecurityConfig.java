package com.app.carimbai.config;

import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.security.JwtAuthenticationFilter;
import com.app.carimbai.security.RateLimitFilter;
import com.app.carimbai.security.TokenRevocationService;
import com.app.carimbai.security.StaffAccessService;
import com.app.carimbai.security.JsonRequestSizeFilter;
import com.app.carimbai.repositories.RefreshTokenRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.app.carimbai.services.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type=org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
@EnableScheduling
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private final JwtService jwtService;
    private final StaffAccessService staffAccess;
    private final RefreshTokenRepository refreshTokens;
    private final JsonRequestSizeFilter jsonRequestSizeFilter;
    private final CustomerRepository customerRepository;
    private final RateLimitFilter rateLimitFilter;
    private final TokenRevocationService revocationService;
    private final CorsProperties corsProperties;
    private final com.app.carimbai.security.RestorationQuarantineFilter quarantine;

    public SecurityConfig(JwtService jwtService, StaffAccessService staffAccess,
                          CustomerRepository customerRepository,
                          RateLimitFilter rateLimitFilter,
                          TokenRevocationService revocationService,
                          CorsProperties corsProperties, RefreshTokenRepository refreshTokens,
                          JsonRequestSizeFilter jsonRequestSizeFilter,
                          com.app.carimbai.security.RestorationQuarantineFilter quarantine) {
        this.jwtService = jwtService;
        this.staffAccess = staffAccess;
        this.refreshTokens = refreshTokens;
        this.jsonRequestSizeFilter = jsonRequestSizeFilter;
        this.customerRepository = customerRepository;
        this.rateLimitFilter = rateLimitFilter;
        this.revocationService = revocationService;
        this.corsProperties = corsProperties;
        this.quarantine=quarantine;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
                jwtService, staffAccess, customerRepository, revocationService, refreshTokens);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        // X-Content-Type-Options e X-Frame-Options já vêm por padrão. SEC-029.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(rp -> rp
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"FORBIDDEN\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout",
                                "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/api/customers/social-login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/notifications/vapid-public-key").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/privacy/notice").permitAll()
                        .requestMatchers("/api/privacy/admin/**").hasAuthority("PRIVACY_OFFICER")
                        .requestMatchers("/api/privacy/me/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE,"/api/notifications/subscriptions").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST,"/api/notifications/unsubscribe-device").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET,"/api/notifications/preferences").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.PUT,"/api/notifications/preferences").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/notifications/subscribe").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/customers/me/enrollment-qr").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/staff/dashboard/metrics", "/api/staff/stamps/recent",
                                "/api/staff/rewards/recent").hasAnyAuthority("CASHIER", "ADMIN")
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/error"
                        ).permitAll()

                        // Enroll = ação de staff (escopo no service + @PreAuthorize no controller). FIX-02 Fase C.
                        .requestMatchers(HttpMethod.POST, "/api/cards").authenticated()
                        // Leitura de cartões e QRs = cliente autenticado; posse validada no service (SEC-001).
                        .requestMatchers(HttpMethod.GET, "/api/cards/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/qr/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/merchants/*/programs").permitAll()

                        .requestMatchers(
                                "/api/auth/switch-merchant",
                                "/api/stamp/**",
                                "/api/redeem/**",
                                "/api/admin/**",
                                "/api/merchants/**",
                                "/api/staff-users/**",
                                // FIX-02 Fase D — /api/customers/** deixa de ser self-service:
                                // createCustomer → PLATFORM_ADMIN; login-or-register → staff (CASHIER/ADMIN/PLATFORM_ADMIN).
                                // Papéis impostos por @PreAuthorize no CustomerController. social-login segue público acima.
                                "/api/customers/**"
                        ).authenticated()

                        .anyRequest().denyAll()
                )
                // FIX-04 — rate limit por IP/endpoint ANTES da auth (brute-force em /login).
                .addFilterBefore(quarantine, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jsonRequestSizeFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<com.app.carimbai.security.RestorationQuarantineFilter> quarantineRegistration(
            com.app.carimbai.security.RestorationQuarantineFilter filter) {
        var registration=new FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitRegistration(RateLimitFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JsonRequestSizeFilter> jsonLimitRegistration(JsonRequestSizeFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // FIX-15 / SEC-030 — origens vêm da config por profile:
        //  - prod (application-prod.yaml): só o PWA da Vercel.
        //  - dev/local (application.yaml base): localhost + Vercel para conveniência.
        config.setAllowedOriginPatterns(corsProperties.allowedOrigins());

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
