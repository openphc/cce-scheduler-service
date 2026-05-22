package org.openphc.cce.scheduler.engine;

import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.scheduler.config.SchedulerProperties;
import org.openphc.cce.scheduler.domain.repository.StepStateSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class StepStateSnapshotService {

    private final StepStateSnapshotRepository snapshotRepository;
    private final SchedulerProperties properties;

    public StepStateSnapshotService(StepStateSnapshotRepository snapshotRepository,
                                    SchedulerProperties properties) {
        this.snapshotRepository = snapshotRepository;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getSnapshot().isEnabled();
    }

    @Transactional
    public void refreshSnapshot() {
        if (!isEnabled()) {
            return;
        }

        try {
            snapshotRepository.refreshGlobalSnapshot();
            log.debug("Refreshed global step state snapshot");
        } catch (Exception e) {
            log.warn("Failed to refresh step state snapshot: {}", e.getMessage(), e);
        }
    }
}
