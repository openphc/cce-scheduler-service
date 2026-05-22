package org.openphc.cce.scheduler.engine;

import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.scheduler.config.SchedulerProperties;
import org.openphc.cce.scheduler.domain.model.TransitionLog;
import org.openphc.cce.scheduler.domain.model.enums.TransitionType;
import org.openphc.cce.scheduler.domain.repository.StepInstanceRepository;
import org.openphc.cce.scheduler.domain.repository.TransitionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class TransitionLogService {

    private final TransitionLogRepository transitionLogRepository;
    private final StepInstanceRepository stepInstanceRepository;
    private final SchedulerProperties properties;

    public TransitionLogService(TransitionLogRepository transitionLogRepository,
                                StepInstanceRepository stepInstanceRepository,
                                SchedulerProperties properties) {
        this.transitionLogRepository = transitionLogRepository;
        this.stepInstanceRepository = stepInstanceRepository;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getTransitionLog().isEnabled();
    }

    @Transactional
    public void logTransitions(List<DueStep> publishedSteps, int partitionIndex) {
        if (!isEnabled() || publishedSteps.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Batch-resolve protocol_definition_ids (avoids N+1)
        Map<UUID, UUID> protocolDefMap = resolveProtocolDefinitionIds(publishedSteps);

        List<TransitionLog> entries = publishedSteps.stream()
                .map(step -> toTransitionLog(step, partitionIndex, now, protocolDefMap))
                .toList();

        transitionLogRepository.saveAll(entries);

        log.debug("Persisted {} transition log entries for partition {}", entries.size(), partitionIndex);
    }

    private Map<UUID, UUID> resolveProtocolDefinitionIds(List<DueStep> steps) {
        List<UUID> protocolInstanceIds = steps.stream()
                .map(DueStep::protocolInstanceId)
                .distinct()
                .toList();

        try {
            List<Object[]> rows = stepInstanceRepository.findProtocolDefinitionIds(protocolInstanceIds);
            Map<UUID, UUID> result = new java.util.HashMap<>();
            for (Object[] row : rows) {
                result.put((UUID) row[0], (UUID) row[1]);
            }
            return result;
        } catch (Exception e) {
            log.warn("Could not batch-resolve protocol_definition_ids: {}", e.getMessage());
            return Map.of();
        }
    }

    private TransitionLog toTransitionLog(DueStep step, int partitionIndex, OffsetDateTime now,
                                          Map<UUID, UUID> protocolDefMap) {
        UUID protocolDefinitionId = protocolDefMap.get(step.protocolInstanceId());
        String fromState = resolveFromState(step.transitionType());
        String toState = resolveToState(step.transitionType());

        return new TransitionLog(
                step.stepInstanceId(),
                step.protocolInstanceId(),
                protocolDefinitionId,
                step.actionId(),
                fromState,
                toState,
                step.transitionType().name(),
                now,
                partitionIndex
        );
    }

    private UUID resolveProtocolDefinitionId(UUID protocolInstanceId) {
        try {
            return stepInstanceRepository.findProtocolDefinitionId(protocolInstanceId);
        } catch (Exception e) {
            log.warn("Could not resolve protocol_definition_id for protocol_instance {}: {}",
                    protocolInstanceId, e.getMessage());
            return null;
        }
    }

    private String resolveFromState(TransitionType type) {
        return switch (type) {
            case PENDING_TO_DUE -> "PENDING";
            case DUE_TO_OVERDUE -> "DUE";
            case OVERDUE_TO_MISSED -> "OVERDUE";
        };
    }

    private String resolveToState(TransitionType type) {
        return switch (type) {
            case PENDING_TO_DUE -> "DUE";
            case DUE_TO_OVERDUE -> "OVERDUE";
            case OVERDUE_TO_MISSED -> "MISSED";
        };
    }
}
