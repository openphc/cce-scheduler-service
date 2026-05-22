# CCE Scheduler Service — Data Dictionary

Comprehensive reference for all database tables, entities, enums, Kafka message fields, configuration properties, and metrics used by the Scheduler Service.

---

## 1. Database Tables

### 1.1 `scheduler_lease` — Partition Leader Election & Heartbeat

Multi-row table used for distributed partitioned leader election and heartbeat tracking. One row exists **per partition** (default: 1 row when `total-partitions=1`). A single instance may own and update **multiple** partition rows via greedy lock acquisition.

**Migration:** `V1__create_scheduler_lease.sql`  
**Owner:** Scheduler Service (read-write)

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `id` | `UUID` | No | `gen_random_uuid()` | Primary key |
| `partition_index` | `INTEGER` | No | `0` | Partition number this row represents (0-based) |
| `leader_id` | `VARCHAR` | Yes | — | Hostname or instance ID of the current partition leader. `NULL` when no leader is active. |
| `last_heartbeat` | `TIMESTAMPTZ` | Yes | — | Last time the partition leader updated this row. Used for staleness detection. |
| `lease_expires_at` | `TIMESTAMPTZ` | Yes | — | When the current lease expires. Computed as `last_heartbeat + leaseDurationSeconds`. |

**Constraints:**
- `PK`: `id`
- `UNIQUE`: `partition_index`

**Row-per-partition pattern:** On startup, the migration inserts row(s) for configured partitions. When scaling, a new migration or application startup logic upserts additional partition rows. All subsequent operations are `UPDATE` — never `DELETE`.

```sql
CREATE TABLE scheduler_lease (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partition_index   INTEGER NOT NULL DEFAULT 0,
    leader_id        VARCHAR,
    last_heartbeat   TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    CONSTRAINT uq_scheduler_lease_partition UNIQUE (partition_index)
);

-- Insert default partition row (single-leader mode)
INSERT INTO scheduler_lease (id, partition_index) VALUES (gen_random_uuid(), 0);
```

### 1.2 `transition_log` — Transition Audit Trail

Append-only log of every time-based state transition triggered by the Scheduler. Used by the Insights Service for trend analysis.

**Migration:** `V2__create_transition_log.sql`  
**Owner:** Scheduler Service (read-write, append-only)

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `id` | `UUID` | No | `gen_random_uuid()` | Primary key |
| `step_instance_id` | `UUID` | No | — | The step that transitioned |
| `protocol_instance_id` | `UUID` | No | — | Owning protocol instance |
| `protocol_definition_id` | `UUID` | Yes | — | Resolved via `protocol_instance` at write time (batch lookup) |
| `action_id` | `VARCHAR(200)` | No | — | PlanDefinition action ID (e.g., `anc-visit-1`) |
| `from_state` | `VARCHAR(20)` | No | — | State before transition (`PENDING`, `DUE`, `OVERDUE`) |
| `to_state` | `VARCHAR(20)` | No | — | State after transition (`DUE`, `OVERDUE`, `MISSED`) |
| `transition_type` | `VARCHAR(30)` | No | — | One of: `PENDING_TO_DUE`, `DUE_TO_OVERDUE`, `OVERDUE_TO_MISSED` |
| `transitioned_at` | `TIMESTAMPTZ` | No | `now()` | When the transition was triggered |
| `partition_index` | `INTEGER` | No | — | Scheduler partition that triggered this |

**Indexes:**
- `idx_transition_log_step` — `(step_instance_id)`
- `idx_transition_log_time` — `(transitioned_at)`
- `idx_transition_log_type` — `(transition_type)`
- `idx_transition_log_protocol` — `(protocol_instance_id)`
- `idx_transition_log_protocol_def` — `(protocol_definition_id)`
- `idx_transition_log_partition` — `(partition_index)` (V5 migration)

### 1.3 `step_state_snapshot` — Pre-Computed State Distribution

Periodic snapshot of step state counts, refreshed at the end of each scan cycle. Avoids expensive `GROUP BY` queries on `step_instance`.

**Migration:** `V3__create_step_state_snapshot.sql`, `V4__fix_snapshot_unique_constraint.sql`  
**Owner:** Scheduler Service (read-write, upsert)

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `id` | `UUID` | No | `gen_random_uuid()` | Primary key |
| `protocol_definition_id` | `UUID` | Yes | — | `NULL` for global snapshot; set for per-protocol breakdowns |
| `facility_id` | `VARCHAR(100)` | Yes | — | `NULL` for global/protocol-level snapshot (future extension) |
| `pending_count` | `INTEGER` | No | `0` | Count of steps in PENDING state |
| `due_count` | `INTEGER` | No | `0` | Count of steps in DUE state |
| `overdue_count` | `INTEGER` | No | `0` | Count of steps in OVERDUE state |
| `missed_count` | `INTEGER` | No | `0` | Count of steps in MISSED state |
| `completed_count` | `INTEGER` | No | `0` | Count of steps in COMPLETED state |
| `skipped_count` | `INTEGER` | No | `0` | Count of steps in SKIPPED state |
| `total_count` | `INTEGER` | No | `0` | Total step count |
| `snapshot_at` | `TIMESTAMPTZ` | No | `now()` | When the snapshot was last refreshed |

**Constraints:**
- Functional unique index: `idx_snapshot_protocol_facility` on `(COALESCE(protocol_definition_id, '00000000-...'::uuid), COALESCE(facility_id, ''))` — handles NULL values correctly for upsert

### 1.4 `step_instance` — Read-Only View (Owned by Compliance Service)

The Scheduler reads this table to find steps that need time-based transitions. **The Scheduler never writes to this table.**

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | `UUID` | No | Primary key |
| `protocol_instance_id` | `UUID` | No | FK to `protocol_instance` |
| `action_id` | `VARCHAR` | No | PlanDefinition action ID (e.g., `anc-visit-1`) |
| `repeat_index` | `INTEGER` | No | 0-based recurrence index |
| `state` | `VARCHAR` | No | Current state — see `StepState` enum |
| `due_date` | `TIMESTAMPTZ` | Yes | When the step becomes DUE |
| `overdue_date` | `TIMESTAMPTZ` | Yes | When the step becomes OVERDUE (due_date + tolerance) |
| `missed_date` | `TIMESTAMPTZ` | Yes | When the step becomes MISSED |
| `completed_at` | `TIMESTAMPTZ` | Yes | When the step was completed (event-driven) |
| `completed_by_source` | `VARCHAR` | Yes | Source system that completed the step |
| `completion_status` | `VARCHAR` | Yes | `EARLY`, `ON_TIME`, or `LATE` |
| `matched_event_id` | `UUID` | Yes | event_log ID of the completing event |
| `required_behavior` | `VARCHAR` | Yes | FHIR `requiredBehavior` code: `must`, `could`, or `must-unless-documented`. Determines MISSED vs SKIPPED on `OVERDUE_TO_MISSED`. |
| `created_at` | `TIMESTAMPTZ` | No | When the step was instantiated |
| `updated_at` | `TIMESTAMPTZ` | No | Last state change |

**Scheduler Query Interest:** Only columns `id`, `protocol_instance_id`, `state`, `due_date`, `overdue_date`, `missed_date`, `required_behavior` are used by the Scheduler's scan query.

---

## 2. Enum Values

### 2.1 `StepState`

State of a step instance in its lifecycle. The Scheduler only queries for `PENDING`, `DUE`, and `OVERDUE` states.

| Value | Description | Scheduler Relevant? |
|-------|-------------|---------------------|
| `PENDING` | Step created but not yet due | Yes — transitions to `DUE` when `due_date ≤ now` |
| `DUE` | Step is now actionable | Yes — transitions to `OVERDUE` when `overdue_date ≤ now` |
| `OVERDUE` | Step is past tolerance window | Yes — transitions to `MISSED` when `missed_date ≤ now` |
| `MISSED` | Step was never completed (terminal) | No — terminal state |
| `COMPLETED` | Step was completed by an event (terminal) | No — terminal state |
| `SKIPPED` | Step was skipped (optional steps only) | No — terminal state |

### 2.2 `TransitionType`

The type of time-based transition the Scheduler requests.

| Value | From State | To State | Condition |
|-------|-----------|----------|-----------|
| `PENDING_TO_DUE` | `PENDING` | `DUE` | `due_date ≤ now` |
| `DUE_TO_OVERDUE` | `DUE` | `OVERDUE` | `overdue_date ≤ now` |
| `OVERDUE_TO_MISSED` | `OVERDUE` | `MISSED` or `SKIPPED` | `missed_date ≤ now`. Compliance Service applies `MISSED` for `must` steps (with deviation) or `SKIPPED` for `could` steps (no deviation). |

---

## 3. Kafka Message Fields

### 3.1 `SchedulerTriggerMessage`

Published to `cce.scheduler.triggers` topic.

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `stepInstanceId` | `UUID` (String) | No | The `step_instance.id` that needs a state transition |
| `transitionType` | `String` | No | One of: `PENDING_TO_DUE`, `DUE_TO_OVERDUE`, `OVERDUE_TO_MISSED` |
| `triggeredAt` | `OffsetDateTime` (ISO 8601) | No | When the scan cycle detected the threshold crossing |
| `correlationid` | `String` | No | Distributed tracing ID — format: `sched-{transitionType}-{stepId-prefix}` (lowercase per CloudEvents convention) |

---

## 4. Internal Records

### 4.1 `DueStep`

Internal record returned by `DueStepScanner` — not persisted or published.

| Field | Type | Description |
|-------|------|-------------|
| `stepInstanceId` | `UUID` | The step that needs transition |
| `protocolInstanceId` | `UUID` | Owning protocol instance (used as Kafka key) |
| `transitionType` | `TransitionType` | Computed transition |
| `thresholdDate` | `OffsetDateTime` | The date threshold that was crossed |
| `metadata` | `JsonNode` | Additional context (e.g., days overdue) |
| `actionId` | `String` | PlanDefinition action ID (e.g., `anc-visit-1`) |

---

## 5. Configuration Properties

All properties are under the `cce.scheduler` prefix.

| Property | Type | Default | Min | Max | Description |
|----------|------|---------|-----|-----|-------------|
| `scan-interval` | `long` (ms) | `5000` | `1000` | `300000` | Delay between scan cycles (`fixedDelay`) |
| `batch-size` | `int` | `100` | `1` | `1000` | Max steps per scan cycle per partition |
| `lease-duration-seconds` | `int` | `30` | `10` | `300` | Lease expiry for leader heartbeat |
| `leader-retry-interval` | `long` (ms) | `5000` | `1000` | `60000` | How often standby retries advisory lock |
| `advisory-lock-key` | `long` | `100001` | — | — | Base PostgreSQL advisory lock key. Partitions use keys `advisory-lock-key + 0` through `advisory-lock-key + total-partitions - 1`. |
| `total-partitions` | `int` | `1` | `1` | `64` | Number of scan partitions. Each partition is an independent advisory lock. `1` = single-leader mode (default). Increase for horizontal scaling. Each instance acquires **all available** partition locks (greedy), so fewer instances than partitions is safe — no orphaned partitions. |
| `lock-acquire-delay-ms` | `int` | `50` | `0` | `500` | Max randomized jitter (ms) between consecutive advisory lock acquisition attempts during startup. Allows concurrent instances to interleave lock acquisitions for fairer partition distribution. Set to `0` to disable. |

---

## 6. Metrics

| Metric Name | Type | Tags | Description |
|-------------|------|------|-------------|
| `cce.scheduler.scan.duration` | Timer | `partition` | Time spent per scan cycle (query + publish) |
| `cce.scheduler.scan.steps` | Counter | `transition_type`, `partition` | Number of steps found per transition type |
| `cce.scheduler.publish.success` | Counter | `transition_type`, `partition` | Successful Kafka publishes per type |
| `cce.scheduler.publish.failure` | Counter | `transition_type`, `partition` | Failed Kafka publishes per type |
| `cce.scheduler.leader.status` | Gauge | `partition` | `1` = partition leader, `0` = standby |
| `cce.scheduler.cycle.count` | Counter | `partition` | Total scan cycles executed (including empty ones) |

---

## 7. Insights Configuration Properties

Additional properties under `cce.scheduler` for the insights optimization features.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `transition-log.enabled` | `boolean` | `true` | Enable/disable transition logging to `transition_log` table |
| `snapshot.enabled` | `boolean` | `true` | Enable/disable step state snapshot refresh |
| `snapshot.protocol-level` | `boolean` | `false` | Enable per-protocol breakdowns in snapshot (future extension) |
