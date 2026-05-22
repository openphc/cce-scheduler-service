package org.openphc.cce.scheduler.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.scheduler.config.SchedulerProperties;
import org.openphc.cce.scheduler.domain.repository.StepStateSnapshotRepository;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class StepStateSnapshotServiceTest {

    @Mock
    private StepStateSnapshotRepository snapshotRepository;

    private SchedulerProperties properties;
    private StepStateSnapshotService service;

    @BeforeEach
    void setUp() {
        properties = new SchedulerProperties();
        service = new StepStateSnapshotService(snapshotRepository, properties);
    }

    @Test
    void refreshSnapshot_enabled_callsRepository() {
        service.refreshSnapshot();

        verify(snapshotRepository).refreshGlobalSnapshot();
    }

    @Test
    void refreshSnapshot_disabled_skips() {
        properties.getSnapshot().setEnabled(false);
        service = new StepStateSnapshotService(snapshotRepository, properties);

        service.refreshSnapshot();

        verify(snapshotRepository, never()).refreshGlobalSnapshot();
    }

    @Test
    void refreshSnapshot_exceptionHandled_doesNotThrow() {
        doThrow(new RuntimeException("DB error")).when(snapshotRepository).refreshGlobalSnapshot();

        // Should not throw
        service.refreshSnapshot();

        verify(snapshotRepository).refreshGlobalSnapshot();
    }
}
