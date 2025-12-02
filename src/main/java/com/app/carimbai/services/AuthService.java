package com.app.carimbai.services;

import com.app.carimbai.dtos.login.LoginRequest;
import com.app.carimbai.dtos.login.LoginResponse;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.repositories.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffUserRepository staffRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        StaffUser user = staffRepo.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalStateException("Staff user is inactive");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(
                token,
                user.getId(),
                user.getMerchant().getId(),
                user.getRole().name(),
                user.getEmail()
        );
    }
}
