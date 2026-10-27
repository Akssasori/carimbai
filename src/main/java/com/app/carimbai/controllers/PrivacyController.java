package com.app.carimbai.controllers;

import com.app.carimbai.dtos.privacy.PrivacyDtos.*;
import com.app.carimbai.services.PrivacyNoticeService;
import com.app.carimbai.services.PrivacyService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/privacy") @RequiredArgsConstructor @Validated
public class PrivacyController {
    private final PrivacyService privacy;
    private final PrivacyNoticeService notices;
    private final com.app.carimbai.services.PrivacyJournalService journal;
    private <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);
    }
    @GetMapping("/notice") @Operation(summary="Privacy notice and publication status; drafts are explicitly identified")
    public ResponseEntity<Notice> notice() { return privateResponse(notices.current()); }
    @GetMapping("/me/profile") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Profile> profile() { return privateResponse(privacy.profile()); }
    @PatchMapping("/me/profile") @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Profile> correct(@Valid @RequestBody UpdateProfile input) { return privateResponse(privacy.correct(input)); }
    @GetMapping("/me/export") @PreAuthorize("isAuthenticated()")
    @Operation(summary="Cursor-paged own data only; credentials and third-party personal data are redacted")
    public ResponseEntity<ExportPage> export(@RequestParam String section,@RequestParam(defaultValue="0") @Min(0) long after,
            @RequestParam(defaultValue="100") @Min(1) @Max(100) int limit) {
        return privateResponse(privacy.export(section,after,limit));
    }
    @PostMapping("/me/requests") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RequestView> open(@Valid @RequestBody OpenRequest input) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(privacy.open(input));
    }
    @GetMapping("/me/requests") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RequestView>> own(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int size) { return privateResponse(privacy.ownRequests(page,size)); }
    @GetMapping("/me/requests/{id}") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RequestView> request(@PathVariable UUID id) { return privateResponse(privacy.ownRequest(id)); }
    @GetMapping("/admin/requests") @PreAuthorize("hasAuthority('PRIVACY_OFFICER')")
    public ResponseEntity<List<RequestView>> queue(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int size) { return privateResponse(privacy.queue(page,size)); }
    @GetMapping("/admin/requests/{id}/preview") @PreAuthorize("hasAuthority('PRIVACY_OFFICER')")
    public ResponseEntity<Map<String,Object>> preview(@PathVariable UUID id) { return privateResponse(privacy.preview(id)); }
    @PostMapping("/admin/requests/{id}/review") @PreAuthorize("hasAuthority('PRIVACY_OFFICER')")
    public ResponseEntity<RequestView> review(@PathVariable UUID id,@Valid @RequestBody Review input) { return privateResponse(privacy.review(id,input)); }
    @GetMapping("/admin/holds") @PreAuthorize("hasAuthority('PRIVACY_OFFICER')")
    public ResponseEntity<List<Map<String,Object>>> holds(@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return privateResponse(privacy.holds(limit));
    }
    @PostMapping("/admin/holds") @PreAuthorize("hasAuthority('PRIVACY_OFFICER')")
    public ResponseEntity<Map<String,UUID>> hold(@Valid @RequestBody Hold input) { return privateResponse(Map.of("id",privacy.hold(input))); }
    @PostMapping("/admin/holds/{id}/release") @PreAuthorize("hasAuthority('PRIVACY_OFFICER')")
    public ResponseEntity<Void> release(@PathVariable UUID id,@Valid @RequestBody ReleaseHold input) {
        privacy.releaseHold(id,input.evidenceRef()); return ResponseEntity.noContent().build();
    }
    @GetMapping("/admin/restoration-journal") @PreAuthorize("hasAuthority('PRIVACY_OFFICER')")
    public ResponseEntity<List<JournalEntry>> journal(@RequestParam(defaultValue="0") @Min(0) long after,
            @RequestParam(defaultValue="100") @Min(1) @Max(100) int limit) { return privateResponse(journal.export(after,limit)); }
}
