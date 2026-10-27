package com.app.carimbai.config;

import com.app.carimbai.dtos.privacy.PrivacyDtos.JournalEntry;
import com.app.carimbai.services.PrivacyJournalService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import java.io.*;
import java.nio.file.*;

/** Offline only. NDJSON journal must be exported and encrypted outside restored backups. */
@Component @Profile("privacy-restore") @RequiredArgsConstructor
public class PrivacyRestoreRunner implements ApplicationRunner {
    private final PrivacyProperties settings;
    private final PrivacyJournalService journal;
    private final ObjectMapper json;
    private final ConfigurableApplicationContext context;
    @Override public void run(ApplicationArguments args) throws Exception {
        if (context instanceof WebApplicationContext || !settings.isRestorationQuarantine()
                || settings.getRestoreJournalPath().isBlank() || settings.getRestoreExpectedEntries()<0
                || settings.getRestoreExpectedLastId()<0) {
            throw new IllegalStateException("Offline quarantine, journal path and independently verified count/high-water mark required");
        }
        long validated=0,applied=0,previousId=0;
        try (var input=new BufferedInputStream(Files.newInputStream(Path.of(settings.getRestoreJournalPath())))) {
            ByteArrayOutputStream line=new ByteArrayOutputStream();
            for (int value;(value=input.read())!=-1;) {
                if (value=='\n') {
                    if (line.size()>0) {
                        JournalEntry entry=json.readValue(line.toByteArray(),JournalEntry.class);
                        if (entry.id()<=previousId) throw new IllegalArgumentException("Journal IDs must be strictly increasing");
                        if (journal.replayOffline(entry,settings.isRestoreDryRun())) applied++;
                        previousId=entry.id(); validated++;
                    }
                    line.reset();
                } else {
                    if (line.size()>=32768) throw new IllegalArgumentException("Journal line exceeds the size limit");
                    line.write(value);
                }
            }
            if (line.size()>0) {
                JournalEntry entry=json.readValue(line.toByteArray(),JournalEntry.class);
                if (entry.id()<=previousId) throw new IllegalArgumentException("Journal IDs must be strictly increasing");
                if (journal.replayOffline(entry,settings.isRestoreDryRun())) applied++;
                previousId=entry.id();
                validated++;
            }
            if (validated!=settings.getRestoreExpectedEntries() || previousId!=settings.getRestoreExpectedLastId()) {
                throw new IllegalStateException("Journal completeness does not match the independently verified manifest");
            }
            System.out.println("Privacy reconciliation: validated="+validated+", applied="+applied+", dryRun="+settings.isRestoreDryRun());
        } finally { context.close(); }
    }
}
