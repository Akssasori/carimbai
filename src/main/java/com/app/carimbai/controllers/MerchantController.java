package com.app.carimbai.controllers;

import com.app.carimbai.dtos.ProgramItemDto;
import com.app.carimbai.dtos.admin.CreateLocationRequest;
import com.app.carimbai.dtos.admin.CreateLocationResponse;
import com.app.carimbai.dtos.admin.CreateMerchantRequest;
import com.app.carimbai.dtos.admin.CreateMerchantResponse;
import com.app.carimbai.dtos.admin.CreateProgramRequest;
import com.app.carimbai.dtos.admin.CreateProgramResponse;
import com.app.carimbai.dtos.admin.UpdateProgramRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final LocationService locationService;
    private final LocationMapper locationMapper;
    private final ProgramService programService;
    private final ProgramMapper programMapper;
    private final MerchantService merchantService;
    private final MerchantMapper merchantMapper;

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation( summary = "Create a new merchant",
            description = "Creates a new merchant with the provided details.")
    @PostMapping
    public ResponseEntity<CreateMerchantResponse> createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantMapper
                .merchanToCreateMerchantResponse(merchantService
                        .createMerchant(request)));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation( summary = "Create a new location for a merchant",
            description = "Creates a new location associated with the specified merchant.")
    @PostMapping("/{merchantId}/locations")
    public ResponseEntity<CreateLocationResponse> createLocation(@PathVariable Long merchantId,
                                                                 @Valid @RequestBody CreateLocationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(locationMapper
                .locationToCreateLocationResponse(locationService
                        .saveLocationByMerchantId(merchantId, request)));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation( summary = "Create a new program for a merchant",
            description = "Creates a new loyalty program/promotion associated with the specified merchant.")
    @PostMapping("/{merchantId}/programs")
    public ResponseEntity<CreateProgramResponse> createProgram(@PathVariable Long merchantId,
                                                               @Valid @RequestBody CreateProgramRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(programMapper
                .programToCreateProgramResponse(programService
                        .createProgram(merchantId, request)));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation( summary = "Update a program/promotion",
            description = "Updates an existing loyalty program/promotion for the specified merchant.")
    @PutMapping("/{merchantId}/programs/{programId}")
    public ResponseEntity<CreateProgramResponse> updateProgram(@PathVariable Long merchantId,
                                                               @PathVariable Long programId,
                                                               @Valid @RequestBody UpdateProgramRequest request) {

        return ResponseEntity.ok(programMapper
                .programToCreateProgramResponse(programService
                        .updateProgram(merchantId, programId, request)));
    }

    @Operation( summary = "List active promotions for a merchant",
            description = "Lists all active programs/promotions for the specified merchant. Optionally include inactive.")
    @GetMapping("/{merchantId}/programs")
    public ResponseEntity<List<ProgramItemDto>> listPrograms(@PathVariable Long merchantId,
                                                             @RequestParam(defaultValue = "true") boolean activeOnly) {

        return ResponseEntity.ok(programService.listPrograms(merchantId, activeOnly)
                .stream().map(programMapper::programToProgramItemDto).toList());
    }
}
