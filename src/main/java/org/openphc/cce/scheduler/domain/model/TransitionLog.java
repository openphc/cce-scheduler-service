package org.openphc.cce.scheduler.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transition_log")
@Getter
@NoArgsConstructor
public class TransitionLog {

    @Id
    private UUID id;

    @Column(name = "step_instance_id", nullable = false)
    private UUID stepInstanceId;

    @Column(name = "protocol_instance_id", nullable = false)
    private UUID protocolInstanceId;

    @Column(name = "protocol_definition_id")
    private UUID protocolDefinitionId;

    @Column(name = "action_id", nullable = false)
    private String actionId;

    @Column(name = "from_state", nullable = false, length = 20)
    private String fromState;

    @Column(name = "to_state", nullable = false, length = 20)
    private String toState;

    @Column(name = "transition_type", nullable = false, length = 30)
    private String transitionType;

    @Column(name = "transitioned_at", nullable = false)
    private OffsetDateTime transitionedAt;

    @Column(name = "partition_index", nullable = false)
    private int partitionIndex;

    public TransitionLog(UUID stepInstanceId, UUID protocolInstanceId, UUID protocolDefinitionId,
                         String actionId, String fromState, String toState,
                         String transitionType, OffsetDateTime transitionedAt, int partitionIndex) {
        this.id = UUID.randomUUID();
        this.stepInstanceId = stepInstanceId;
        this.protocolInstanceId = protocolInstanceId;
        this.protocolDefinitionId = protocolDefinitionId;
        this.actionId = actionId;
        this.fromState = fromState;
        this.toState = toState;
        this.transitionType = transitionType;
        this.transitionedAt = transitionedAt;
        this.partitionIndex = partitionIndex;
    }
}
