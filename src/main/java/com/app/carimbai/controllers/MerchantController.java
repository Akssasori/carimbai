package com.app.carimbai.controllers;

import com.app.carimbai.dtos.admin.CreateLocationRequest;
import com.app.carimbai.dtos.admin.CreateLocationResponse;
import com.app.carimbai.dtos.admin.CreateMerchantRequest;
import com.app.carimbai.dtos.admin.CreateMerchantResponse;
import com.app.carimbai.dtos.admin.CreateProgramRequest;
import com.app.carimbai.dtos.admin.CreateProgramResponse;
import com.app.carimbai.mappers.LocationMapper;
import com.app.carimbai.mappers.MerchantMapper;
import com.app.carimbai.mappers.ProgramMapper;
import com.app.carimbai.services.LocationService;
import com.app.carimbai.services.MerchantService;
import com.app.carimbai.services.ProgramService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class MerchantController {

    private final LocationService locationService;
    private final LocationMapper locationMapper;
    private final ProgramService programService;
    private final ProgramMapper programMapper;
    private final MerchantService merchantService;
    private final MerchantMapper merchantMapper;

    @Operation( summary = "Create a new merchant",
            description = "Creates a new merchant with the provided details.")
    @PostMapping
    public ResponseEntity<CreateMerchantResponse> createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantMapper
                .merchanToCreateMerchantResponse(merchantService
                        .createMerchant(request)));
    }

    @Operation( summary = "Create a new location for a merchant",
            description = "Creates a new location associated with the specified merchant.")
    @PostMapping("/{merchantId}/locations")
    public ResponseEntity<CreateLocationResponse> createLocation(@PathVariable Long merchantId,
                                                                 @Valid @RequestBody CreateLocationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(locationMapper
                .locationToCreateLocationResponse(locationService
                        .saveLocationByMerchantId(merchantId, request)));
    }

    @Operation( summary = "Create a new program for a merchant",
            description = "Creates a new loyalty program associated with the specified merchant.")
    @PostMapping("/{merchantId}/programs")
    public ResponseEntity<CreateProgramResponse> createProgram(@PathVariable Long merchantId,
                                                               @Valid @RequestBody CreateProgramRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(programMapper
                .programToCreateProgramResponse(programService
                        .createProgram(merchantId, request)));
    }
}
