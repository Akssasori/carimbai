package com.app.carimbai.controllers;

import com.app.carimbai.dtos.admin.CreateStaffUserRequest;
import com.app.carimbai.dtos.admin.CreateStaffUserResponse;
import com.app.carimbai.mappers.StaffMapper;
import com.app.carimbai.services.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff-users")
@RequiredArgsConstructor
public class StaffUserController {

    private final StaffMapper staffMapper;
    private final StaffService staffService;

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation( summary = "Create a new staff user",
            description = "Creates a new staff user with the provided details.")
    @PostMapping
    public ResponseEntity<CreateStaffUserResponse> createStaffUser(@Valid @RequestBody CreateStaffUserRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(staffMapper
                .staffUserToCreateStaffUserResponse(staffService
                        .createStaffUser(request)));

    }
}
