package org.openphc.cce.scheduler.engine;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.scheduler.config.SchedulerProperties;
import org.openphc.cce.scheduler.domain.model.TransitionLog;
import org.openphc.cce.scheduler.domain.model.enums.TransitionType;
import org.openphc.cce.scheduler.domain.repository.StepInstanceRepository;
import org.openphc.cce.scheduler.domain.repository.TransitionLogRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransitionLogServiceTest {

    @Mock
    private TransitionLogRepository transitionLogRepository;

    @Mock
    private StepInstanceRepository stepInstanceRepository;

    private SchedulerProperties properties;
    private TransitionLogService service;

    @BeforeEach
    void setUp() {
        properties = new SchedulerProperties();
        service = new TransitionLogService(transitionLogRepository, stepInstanceRepository, properties);
    }

    @Test
    void logTransitions_enabled_persistsEntries() {
        UUID protocolDefId = UUID.randomUUID();
        UUID protocolInstanceId = UUID.randomUUID();
        DueStep step = buildDueStep(TransitionType.PENDING_TO_DUE, protocolInstanceId);

        when(stepInstanceRepository.findProtocolDefinitionIds(List.of(protocolInstanceId)))
                .thenReturn(List.<Object[]>of(new Object[]{protocolInstanceId, protocolDefId}));

        service.logTransitions(List.of(step), 0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransitionLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(transitionLogRepository).saveAll(captor.capture());

        List<TransitionLog> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStepInstanceId()).isEqualTo(step.stepInstanceId());
        assertThat(saved.get(0).getProtocolInstanceId()).isEqualTo(protocolInstanceId);
        assertThat(saved.get(0).getProtocolDefinitionId()).isEqualTo(protocolDefId);
        assertThat(saved.get(0).getActionId()).isEqualTo("action-1");
        assertThat(saved.get(0).getFromState()).isEqualTo("PENDING");
        assertThat(saved.get(0).getToState()).isEqualTo("DUE");
        assertThat(saved.get(0).getTransitionType()).isEqualTo("PENDING_TO_DUE");
        assertThat(saved.get(0).getPartitionIndex()).isEqualTo(0);
    }

    @Test
    void logTransitions_dueToOverdue_correctStates() {
        UUID protocolInstanceId = UUID.randomUUID();
        DueStep step = buildDueStep(TransitionType.DUE_TO_OVERDUE, protocolInstanceId);

        when(stepInstanceRepository.findProtocolDefinitionIds(List.of(protocolInstanceId)))
                .thenReturn(List.<Object[]>of(new Object[]{protocolInstanceId, null}));

        service.logTransitions(List.of(step), 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransitionLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(transitionLogRepository).saveAll(captor.capture());

        TransitionLog entry = captor.getValue().get(0);
        assertThat(entry.getFromState()).isEqualTo("DUE");
        assertThat(entry.getToState()).isEqualTo("OVERDUE");
        assertThat(entry.getTransitionType()).isEqualTo("DUE_TO_OVERDUE");
    }

    @Test
    void logTransitions_overdueToMissed_correctStates() {
        UUID protocolInstanceId = UUID.randomUUID();
        DueStep step = buildDueStep(TransitionType.OVERDUE_TO_MISSED, protocolInstanceId);

        when(stepInstanceRepository.findProtocolDefinitionIds(List.of(protocolInstanceId)))
                .thenReturn(List.<Object[]>of(new Object[]{protocolInstanceId, null}));

        service.logTransitions(List.of(step), 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransitionLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(transitionLogRepository).saveAll(captor.capture());

        TransitionLog entry = captor.getValue().get(0);
        assertThat(entry.getFromState()).isEqualTo("OVERDUE");
        assertThat(entry.getToState()).isEqualTo("MISSED");
    }

    @Test
    void logTransitions_disabled_doesNotPersist() {
        properties.getTransitionLog().setEnabled(false);
        service = new TransitionLogService(transitionLogRepository, stepInstanceRepository, properties);

        DueStep step = buildDueStep(TransitionType.PENDING_TO_DUE, UUID.randomUUID());

        service.logTransitions(List.of(step), 0);

        verify(transitionLogRepository, never()).saveAll(any());
    }

    @Test
    void logTransitions_emptyList_doesNotPersist() {
        service.logTransitions(Collections.emptyList(), 0);

        verify(transitionLogRepository, never()).saveAll(any());
    }

    @Test
    void logTransitions_protocolDefResolutionFails_setsNull() {
        UUID protocolInstanceId = UUID.randomUUID();
        DueStep step = buildDueStep(TransitionType.PENDING_TO_DUE, protocolInstanceId);

        when(stepInstanceRepository.findProtocolDefinitionIds(List.of(protocolInstanceId)))
                .thenThrow(new RuntimeException("table not found"));

        service.logTransitions(List.of(step), 0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransitionLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(transitionLogRepository).saveAll(captor.capture());

        assertThat(captor.getValue().get(0).getProtocolDefinitionId()).isNull();
    }

    private DueStep buildDueStep(TransitionType type, UUID protocolInstanceId) {
        return new DueStep(
                UUID.randomUUID(),
                protocolInstanceId,
                type,
                OffsetDateTime.now(ZoneOffset.UTC),
                JsonNodeFactory.instance.objectNode(),
                "action-1"
        );
    }
}
