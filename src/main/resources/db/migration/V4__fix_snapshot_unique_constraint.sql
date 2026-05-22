-- Fix: PostgreSQL UNIQUE constraint doesn't work with NULL values
-- Replace table-level UNIQUE with a functional unique index using COALESCE
ALTER TABLE step_state_snapshot DROP CONSTRAINT IF EXISTS step_state_snapshot_protocol_definition_id_facility_id_key;

CREATE UNIQUE INDEX idx_snapshot_protocol_facility
    ON step_state_snapshot (
        COALESCE(protocol_definition_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(facility_id, '')
    );
