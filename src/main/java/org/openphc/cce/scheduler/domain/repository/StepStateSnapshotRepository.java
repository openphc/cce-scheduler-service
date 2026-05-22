package org.openphc.cce.scheduler.domain.repository;

import org.openphc.cce.scheduler.domain.model.StepStateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StepStateSnapshotRepository extends JpaRepository<StepStateSnapshot, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO step_state_snapshot (id, protocol_definition_id, facility_id,
                pending_count, due_count, overdue_count, missed_count,
                completed_count, skipped_count, total_count, snapshot_at)
            SELECT
                gen_random_uuid(), NULL, NULL,
                COUNT(*) FILTER (WHERE si.state = 'PENDING'),
                COUNT(*) FILTER (WHERE si.state = 'DUE'),
                COUNT(*) FILTER (WHERE si.state = 'OVERDUE'),
                COUNT(*) FILTER (WHERE si.state = 'MISSED'),
                COUNT(*) FILTER (WHERE si.state = 'COMPLETED'),
                COUNT(*) FILTER (WHERE si.state = 'SKIPPED'),
                COUNT(*),
                now()
            FROM step_instance si
            ON CONFLICT (COALESCE(protocol_definition_id, '00000000-0000-0000-0000-000000000000'::uuid), COALESCE(facility_id, ''))
            DO UPDATE SET
                pending_count = EXCLUDED.pending_count,
                due_count = EXCLUDED.due_count,
                overdue_count = EXCLUDED.overdue_count,
                missed_count = EXCLUDED.missed_count,
                completed_count = EXCLUDED.completed_count,
                skipped_count = EXCLUDED.skipped_count,
                total_count = EXCLUDED.total_count,
                snapshot_at = now()
            """, nativeQuery = true)
    void refreshGlobalSnapshot();
}
