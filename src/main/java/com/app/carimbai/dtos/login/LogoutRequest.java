package com.app.carimbai.dtos.login;

import jakarta.validation.constraints.Size;

public record LogoutRequest(@Size(max = 256) String refreshToken) {}
