package com.app.carimbai.dtos.customer;

import java.util.UUID;

public record EnrollmentQrResponse(String type, Long customerId, UUID nonce, long exp, String sig) {}
