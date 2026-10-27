package com.app.carimbai.services;

import com.app.carimbai.config.PrivacyProperties;
import com.app.carimbai.dtos.privacy.PrivacyDtos.Notice;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class PrivacyNoticeService {
    private final PrivacyProperties settings;
    private String text;
    @PostConstruct void load() throws IOException {
        if (!settings.getNoticeResource().matches("privacy/notice-[A-Za-z0-9_-]+\\.txt")) {
            throw new IllegalArgumentException("Privacy notice must be a reviewed classpath resource");
        }
        text=new ClassPathResource(settings.getNoticeResource())
                .getContentAsString(StandardCharsets.UTF_8);
        // This release ships a draft only. A boolean must not publish fictitious identity.
        if (settings.isNoticeApproved() && (settings.getNoticeVersion().startsWith("draft")
                || !settings.getNoticeResource().equals("privacy/notice-"+settings.getNoticeVersion()+".txt")
                || text.startsWith("RASCUNHO") || settings.getNoticeApprovalRef().isBlank()
                || settings.getControllerName().isBlank() || settings.getControllerName().contains("pendente")
                || !validCnpj(settings.getControllerDocument())
                || !settings.getContactEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
            throw new IllegalStateException("Review and replace draft notice and fictitious controller identity before publication");
        }
    }
    public Notice current() {
        return new Notice(settings.isNoticeApproved(),settings.getNoticeVersion(),settings.getControllerName(),
                settings.getControllerDocument(),settings.getContactEmail(),text,
                settings.isNoticeApproved() ? List.of() : List.of("CONTROLLER_IDENTITY","LEGAL_BASES","RETENTION","VENDORS_AND_TRANSFERS","AUDIENCE_REVIEW"));
    }
    public void requirePublished(String version) {
        Notice notice=current();
        if (!notice.published() || !notice.version().equals(version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Published privacy notice required for new optional processing");
        }
    }
    private boolean validCnpj(String document) {
        String digits=document.toUpperCase(java.util.Locale.ROOT).replaceAll("[./\\s-]","");
        if (!digits.matches("[A-Z0-9]{12}[0-9]{2}") || digits.chars().distinct().count()==1) return false;
        for (int position=12;position<14;position++) {
            int sum=0,weight=2;
            for (int index=position-1;index>=0;index--) {
                sum+=(digits.charAt(index)-'0')*weight;
                weight=weight==9 ? 2 : weight+1;
            }
            int expected=sum%11<2 ? 0 : 11-sum%11;
            if (digits.charAt(position)-'0'!=expected) return false;
        }
        return true;
    }
}
