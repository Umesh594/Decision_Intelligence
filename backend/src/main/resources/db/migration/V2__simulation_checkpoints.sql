-- Supports idempotent / resumable simulation runs: a run can now be IN_PROGRESS (not yet
-- COMPLETED or FAILED), is looked up by idempotency_key so retries don't re-run completed work,
-- and summary_json/ai_insight are only populated once the run finishes.
ALTER TABLE simulation_runs ALTER COLUMN summary_json DROP NOT NULL;
ALTER TABLE simulation_runs ADD COLUMN idempotency_key VARCHAR(128);
ALTER TABLE simulation_runs ADD COLUMN target_iterations INTEGER;
ALTER TABLE simulation_runs ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX idx_simulation_runs_idempotency_key ON simulation_runs(idempotency_key);

-- Per-(run, decision path) checkpoint of the concurrent Monte Carlo engine's progress: exact
-- running aggregates (counts/sums) plus a bounded reservoir sample for percentile estimation.
-- See com.umesh.decision.domain.SimulationCheckpoint for the resumability contract.
CREATE TABLE simulation_checkpoints (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES simulation_runs(id),
    path_name VARCHAR(160) NOT NULL,
    next_batch_index INTEGER NOT NULL DEFAULT 0,
    completed_iterations BIGINT NOT NULL DEFAULT 0,
    goal_hits BIGINT NOT NULL DEFAULT 0,
    downside_events BIGINT NOT NULL DEFAULT 0,
    recovery_hits BIGINT NOT NULL DEFAULT 0,
    sum_final_salary DOUBLE PRECISION NOT NULL DEFAULT 0,
    sum_final_salary_sq DOUBLE PRECISION NOT NULL DEFAULT 0,
    sum_wealth DOUBLE PRECISION NOT NULL DEFAULT 0,
    yearly_totals_json TEXT NOT NULL,
    reservoir_json TEXT NOT NULL,
    reservoir_seen_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_simulation_checkpoints_run_path UNIQUE (run_id, path_name)
);

CREATE INDEX idx_simulation_checkpoints_run_id ON simulation_checkpoints(run_id);
