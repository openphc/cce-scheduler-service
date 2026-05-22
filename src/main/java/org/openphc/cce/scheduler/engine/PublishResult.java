package org.openphc.cce.scheduler.engine;

import java.util.List;

public record PublishResult(
        int successCount,
        List<DueStep> publishedSteps
) {
}
