package org.openphc.cce.scheduler.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "step_state_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class StepStateSnapshot {

    @Id
    private UUID id;

    @Column(name = "protocol_definition_id")
    private UUID protocolDefinitionId;

    @Column(name = "facility_id", length = 100)
    private String facilityId;

    @Column(name = "pending_count", nullable = false)
    private int pendingCount;

    @Column(name = "due_count", nullable = false)
    private int dueCount;

    @Column(name = "overdue_count", nullable = false)
    private int overdueCount;

    @Column(name = "missed_count", nullable = false)
    private int missedCount;

    @Column(name = "completed_count", nullable = false)
    private int completedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "snapshot_at", nullable = false)
    private OffsetDateTime snapshotAt;

    public StepStateSnapshot(UUID protocolDefinitionId, String facilityId) {
        this.id = UUID.randomUUID();
        this.protocolDefinitionId = protocolDefinitionId;
        this.facilityId = facilityId;
        this.snapshotAt = OffsetDateTime.now();
    }
}
