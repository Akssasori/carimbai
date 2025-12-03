package com.app.carimbai.controllers;


import com.app.carimbai.dtos.admin.SetPinRequest;
import com.app.carimbai.services.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final StaffService staffService;

    @Operation(summary = "Set staff user PIN")
    @PostMapping("/staff-users/{id}/pin")
    public ResponseEntity<?> setStaffPin(@PathVariable Long id,
                                         @RequestBody SetPinRequest request) {
        staffService.setPin(id, request.pin());
        return ResponseEntity.ok().build();
    }


}
