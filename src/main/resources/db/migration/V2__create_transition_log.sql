CREATE TABLE transition_log (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step_instance_id       UUID NOT NULL,
    protocol_instance_id   UUID NOT NULL,
    protocol_definition_id UUID,
    action_id              VARCHAR(200) NOT NULL,
    from_state             VARCHAR(20) NOT NULL,
    to_state               VARCHAR(20) NOT NULL,
    transition_type        VARCHAR(30) NOT NULL,
    transitioned_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    partition_index        INTEGER NOT NULL
);

CREATE INDEX idx_transition_log_step ON transition_log (step_instance_id);
CREATE INDEX idx_transition_log_time ON transition_log (transitioned_at);
CREATE INDEX idx_transition_log_type ON transition_log (transition_type);
CREATE INDEX idx_transition_log_protocol ON transition_log (protocol_instance_id);
CREATE INDEX idx_transition_log_protocol_def ON transition_log (protocol_definition_id);
