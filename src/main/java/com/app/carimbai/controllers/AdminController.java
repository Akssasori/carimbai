package com.app.carimbai.controllers;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.admin.CreateCustomerResponse;
import com.app.carimbai.dtos.admin.CreateLocationRequest;
import com.app.carimbai.dtos.admin.CreateLocationResponse;
import com.app.carimbai.dtos.admin.CreateMerchantRequest;
import com.app.carimbai.dtos.admin.CreateMerchantResponse;
import com.app.carimbai.dtos.admin.CreateProgramRequest;
import com.app.carimbai.dtos.admin.CreateProgramResponse;
import com.app.carimbai.dtos.admin.CreateStaffUserRequest;
import com.app.carimbai.dtos.admin.CreateStaffUserResponse;
import com.app.carimbai.mappers.CustomerMapper;
import com.app.carimbai.mappers.LocationMapper;
import com.app.carimbai.mappers.MerchantMapper;
import com.app.carimbai.mappers.ProgramMapper;
import com.app.carimbai.mappers.StaffMapper;
import com.app.carimbai.services.CustomerService;
import com.app.carimbai.services.LocationService;
import com.app.carimbai.services.MerchantService;
import com.app.carimbai.services.ProgramService;
import com.app.carimbai.services.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    private final LocationService locationService;
    private final LocationMapper locationMapper;
    private final ProgramService programService;
    private final ProgramMapper programMapper;
    private final MerchantService merchantService;
    private final MerchantMapper merchantMapper;
    private final StaffMapper staffMapper;
    private final StaffService staffService;
    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    @PostMapping("/merchants")
    public ResponseEntity<CreateMerchantResponse> createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantMapper
                .merchanToCreateMerchantResponse(merchantService
                        .createMerchant(request)));
    }

    @PostMapping("/merchants/{merchantId}/locations")
    public ResponseEntity<CreateLocationResponse> createLocation(@PathVariable Long merchantId,
                                                                 @Valid @RequestBody CreateLocationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(locationMapper
                .locationToCreateLocationResponse(locationService
                .saveLocationByMerchantId(merchantId, request)));
    }

    @PostMapping("/merchants/{merchantId}/programs")
    public ResponseEntity<CreateProgramResponse> createProgram(@PathVariable Long merchantId,
                                                               @Valid @RequestBody CreateProgramRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(programMapper
                .programToCreateProgramResponse(programService
                .createProgram(merchantId, request)));
    }

    @PostMapping("/staff-users")
    public ResponseEntity<CreateStaffUserResponse> createStaffUser(@Valid @RequestBody CreateStaffUserRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(staffMapper
                .staffUserToCreateStaffUserResponse(staffService
                        .createStaffUser(request)));

    }

    @PostMapping("/customers")
    public ResponseEntity<CreateCustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(customerMapper
                .customerToCreateCustomerResponse(customerService
                        .createCustomer(request)));

    }


}
