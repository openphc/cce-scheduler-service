CREATE TABLE step_state_snapshot (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocol_definition_id UUID,
    facility_id            VARCHAR(100),
    pending_count          INTEGER NOT NULL DEFAULT 0,
    due_count              INTEGER NOT NULL DEFAULT 0,
    overdue_count          INTEGER NOT NULL DEFAULT 0,
    missed_count           INTEGER NOT NULL DEFAULT 0,
    completed_count        INTEGER NOT NULL DEFAULT 0,
    skipped_count          INTEGER NOT NULL DEFAULT 0,
    total_count            INTEGER NOT NULL DEFAULT 0,
    snapshot_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (protocol_definition_id, facility_id)
);
