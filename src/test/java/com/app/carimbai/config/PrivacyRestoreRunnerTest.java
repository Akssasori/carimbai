package com.app.carimbai.config;

import com.app.carimbai.services.PrivacyJournalService;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivacyRestoreRunnerTest {
    @TempDir Path temporary;
    @Test void missingIndependentManifestFailsClosed() {
        var settings=new PrivacyProperties(); settings.setRestorationQuarantine(true); settings.setRestoreJournalPath("test-only.ndjson");
        var runner=new PrivacyRestoreRunner(settings,mock(PrivacyJournalService.class),JsonMapper.builder().build(),mock(ConfigurableApplicationContext.class));
        assertThatThrownBy(()->runner.run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
    }
    @Test void truncatedJournalCannotBeReportedAsSuccessfulReconciliation() throws Exception {
        var settings=new PrivacyProperties(); settings.setRestorationQuarantine(true);
        Path file=Files.createFile(temporary.resolve("journal.ndjson"));
        settings.setRestoreJournalPath(file.toString()); settings.setRestoreExpectedEntries(1); settings.setRestoreExpectedLastId(1);
        var context=mock(ConfigurableApplicationContext.class);
        var runner=new PrivacyRestoreRunner(settings,mock(PrivacyJournalService.class),JsonMapper.builder().build(),context);
        assertThatThrownBy(()->runner.run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class).hasMessageContaining("completeness");
        verify(context).close();
    }
    @Test void emptyManifestAndEmptyJournalAreAValidDryRun() throws Exception {
        var settings=new PrivacyProperties(); settings.setRestorationQuarantine(true);
        Path file=Files.createFile(temporary.resolve("empty.ndjson"));
        settings.setRestoreJournalPath(file.toString()); settings.setRestoreExpectedEntries(0); settings.setRestoreExpectedLastId(0);
        var journal=mock(PrivacyJournalService.class); var context=mock(ConfigurableApplicationContext.class);
        new PrivacyRestoreRunner(settings,journal,JsonMapper.builder().build(),context).run(new DefaultApplicationArguments());
        verifyNoInteractions(journal); verify(context).close();
    }
}
