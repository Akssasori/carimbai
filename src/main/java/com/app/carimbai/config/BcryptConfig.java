package com.app.carimbai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class BcryptConfig {

    // Custo 12 (SEC-016). Hashes antigos (custo 10) continuam válidos: o custo é
    // lido do próprio hash na verificação.
    @Bean
    BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
