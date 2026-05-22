-- Add missing index on partition_index for partition-scoped queries
CREATE INDEX idx_transition_log_partition ON transition_log (partition_index);
