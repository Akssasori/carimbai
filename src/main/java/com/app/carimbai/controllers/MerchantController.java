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
import com.app.carimbai.mappers.LocationMapper;
import com.app.carimbai.mappers.MerchantMapper;
import com.app.carimbai.mappers.ProgramMapper;
import com.app.carimbai.mappers.StaffMapper;
import com.app.carimbai.services.LocationService;
import com.app.carimbai.services.MerchantService;
import com.app.carimbai.services.ProgramService;
import com.app.carimbai.services.StaffService;
import com.app.carimbai.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final StaffService staffService;
    private final StaffMapper staffMapper;

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Create a new merchant",
            description = "Creates a new merchant with the provided details.")
    @PostMapping
    public ResponseEntity<CreateMerchantResponse> createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantMapper
                .merchanToCreateMerchantResponse(merchantService
                        .createMerchant(request)));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Create a new location for a merchant",
            description = "Creates a new location associated with the specified merchant.")
    @PostMapping("/{merchantId}/locations")
    public ResponseEntity<CreateLocationResponse> createLocation(@PathVariable Long merchantId,
                                                                 @Valid @RequestBody CreateLocationRequest request) {
        SecurityUtils.requirePathMerchantMatchesActive(merchantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(locationMapper
                .locationToCreateLocationResponse(locationService
                        .saveLocationByMerchantId(merchantId, request)));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "List locations of a merchant",
            description = "Lista todas as locations do merchant ativo. Inclui flags e active flag.")
    @GetMapping("/{merchantId}/locations")
    public ResponseEntity<List<LocationItem>> listLocations(@PathVariable Long merchantId) {
        SecurityUtils.requirePathMerchantMatchesActive(merchantId);
        return ResponseEntity.ok(
                locationService.listByMerchant(merchantId).stream()
                        .map(loc -> locationMapper.toLocationItem(loc, locationService.parseFlags(loc)))
                        .toList()
        );
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Update a location",
            description = "Atualiza name, address, active e/ou flags de uma location do merchant ativo. " +
                    "Quando `flags` vem != null, sobrescreve o JSONB inteiro (front deve enviar os 3 toggles).")
    @PutMapping("/{merchantId}/locations/{locationId}")
    public ResponseEntity<LocationItem> updateLocation(@PathVariable Long merchantId,
                                                       @PathVariable Long locationId,
                                                       @Valid @RequestBody UpdateLocationRequest request) {
        SecurityUtils.requirePathMerchantMatchesActive(merchantId);
        var updated = locationService.updateLocation(merchantId, locationId, request);
        return ResponseEntity.ok(locationMapper.toLocationItem(updated, locationService.parseFlags(updated)));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Create a new program for a merchant",
            description = "Creates a new loyalty program/promotion associated with the specified merchant.")
    @PostMapping("/{merchantId}/programs")
    public ResponseEntity<CreateProgramResponse> createProgram(@PathVariable Long merchantId,
                                                               @Valid @RequestBody CreateProgramRequest request) {
        SecurityUtils.requirePathMerchantMatchesActive(merchantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(programMapper
                .programToCreateProgramResponse(programService
                        .createProgram(merchantId, request)));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Update a program/promotion",
            description = "Updates an existing loyalty program/promotion for the specified merchant.")
    @PutMapping("/{merchantId}/programs/{programId}")
    public ResponseEntity<CreateProgramResponse> updateProgram(@PathVariable Long merchantId,
                                                               @PathVariable Long programId,
                                                               @Valid @RequestBody UpdateProgramRequest request) {
        SecurityUtils.requirePathMerchantMatchesActive(merchantId);
        return ResponseEntity.ok(programMapper
                .programToCreateProgramResponse(programService
                        .updateProgram(merchantId, programId, request)));
    }

    @Operation(summary = "List active promotions for a merchant (public)",
            description = "Lista programs/promotions ativos. Endpoint publico para o app do cliente. " +
                    "Para visualizacao admin (incluindo inativos) use /admin/programs.")
    @GetMapping("/{merchantId}/programs")
    public ResponseEntity<List<ProgramItemDto>> listPrograms(@PathVariable Long merchantId,
                                                             @RequestParam(defaultValue = "true") boolean activeOnly) {

        return ResponseEntity.ok(programService.listPrograms(merchantId, activeOnly)
                .stream().map(programMapper::programToProgramItemDto).toList());
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "List all programs of a merchant for admin view",
            description = "Lista todos os programs (ativos e inativos) com todos os campos. ADMIN-only.")
    @GetMapping("/{merchantId}/admin/programs")
    public ResponseEntity<List<AdminProgramItem>> listAdminPrograms(@PathVariable Long merchantId) {
        SecurityUtils.requirePathMerchantMatchesActive(merchantId);
        return ResponseEntity.ok(
                programService.listPrograms(merchantId, false).stream()
                        .map(programMapper::programToAdminProgramItem)
                        .toList()
        );
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "List staff of a merchant",
            description = "Lista staff do merchant ativo com role e active. Vinculo via core.staff_user_merchants.")
    @GetMapping("/{merchantId}/staff-users")
    public ResponseEntity<List<StaffItem>> listStaff(@PathVariable Long merchantId) {
        SecurityUtils.requirePathMerchantMatchesActive(merchantId);
        return ResponseEntity.ok(
                staffService.listStaffByMerchant(merchantId).stream()
                        .map(staffMapper::toStaffItem)
                        .toList()
        );
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Update staff role/active in the active merchant",
            description = "Patch parcial do vinculo staff_user_merchants. " +
                    "Guards: admin nao pode se desativar ou se rebaixar de ADMIN para CASHIER no proprio merchant.")
    @PatchMapping("/{merchantId}/staff-users/{staffId}")
    public ResponseEntity<StaffItem> updateStaff(@PathVariable Long merchantId,
                                                 @PathVariable Long staffId,
                                                 @Valid @RequestBody UpdateStaffMerchantRequest request) {
        SecurityUtils.requirePathMerchantMatchesActive(merchantId);
        Long callerStaffId = SecurityUtils.getRequiredStaffUser().getId();
        var updated = staffService.updateStaffInMerchant(merchantId, staffId, request, callerStaffId);
        return ResponseEntity.ok(staffMapper.toStaffItem(updated));
    }
}
