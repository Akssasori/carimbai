package com.app.carimbai.controllers;

import com.app.carimbai.services.PrivacyRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/privacy/admin/retention") @RequiredArgsConstructor
@PreAuthorize("hasAuthority('PRIVACY_OFFICER')")
public class PrivacyRetentionController {
    private final PrivacyRetentionService retention;
    @PostMapping("/preview") public ResponseEntity<List<Map<String,Object>>> preview() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(retention.run(true));
    }
    @PostMapping("/run") public ResponseEntity<List<Map<String,Object>>> run() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(retention.run(false));
    }
}
