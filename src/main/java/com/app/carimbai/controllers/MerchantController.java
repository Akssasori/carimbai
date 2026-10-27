package com.app.carimbai.controllers;

import com.app.carimbai.dtos.ProgramItemDto;
import com.app.carimbai.dtos.admin.CreateLocationRequest;
import com.app.carimbai.dtos.admin.CreateLocationResponse;
import com.app.carimbai.dtos.admin.CreateMerchantRequest;
import com.app.carimbai.dtos.admin.CreateMerchantResponse;
import com.app.carimbai.dtos.admin.CreateProgramRequest;
import com.app.carimbai.dtos.admin.CreateProgramResponse;
import com.app.carimbai.dtos.admin.UpdateProgramRequest;
import com.app.carimbai.dtos.staff.admin.AdminProgramItem;
import com.app.carimbai.dtos.staff.admin.LocationItem;
import com.app.carimbai.dtos.staff.admin.StaffItem;
import com.app.carimbai.dtos.staff.admin.UpdateLocationRequest;
import com.app.carimbai.dtos.staff.admin.UpdateStaffMerchantRequest;
import com.app.carimbai.services.StaffService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
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
@Validated
public class MerchantController {

    private final LocationService locationService;
    private final LocationMapper locationMapper;
    private final ProgramService programService;
    private final ProgramMapper programMapper;
    private final MerchantService merchantService;
    private final MerchantMapper merchantMapper;
    private final StaffService staffService;

    @GetMapping("/{merchantId}/admin/programs")
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<AdminProgramItem> adminPrograms(@PathVariable Long merchantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return programService.listAdminPrograms(merchantId, page, size).stream()
                .map(programMapper::programToAdminProgramItem).toList();
    }

    @GetMapping("/{merchantId}/locations")
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<LocationItem> locations(@PathVariable Long merchantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return locationService.list(merchantId, page, size);
    }

    @PutMapping("/{merchantId}/locations/{locationId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public LocationItem updateLocation(@PathVariable Long merchantId, @PathVariable Long locationId,
                                      @Valid @RequestBody UpdateLocationRequest request) {
        return locationService.update(merchantId, locationId, request);
    }

    @GetMapping("/{merchantId}/staff-users")
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<StaffItem> staff(@PathVariable Long merchantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return staffService.listStaff(merchantId, page, size);
    }

    @PatchMapping("/{merchantId}/staff-users/{staffId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public StaffItem updateStaff(@PathVariable Long merchantId, @PathVariable Long staffId,
                                @Valid @RequestBody UpdateStaffMerchantRequest request) {
        return staffService.updateInMerchant(merchantId, staffId, request);
    }

    // Onboarding de lojista é ação de plataforma, não de um ADMIN de merchant (SEC-020).
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @Operation( summary = "Create a new merchant",
            description = "Creates a new merchant with the provided details. Requires PLATFORM_ADMIN.")
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
