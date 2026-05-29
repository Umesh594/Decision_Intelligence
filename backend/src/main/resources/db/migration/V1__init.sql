CREATE TABLE decision_scenarios (
    id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    location VARCHAR(120) NOT NULL,
    current_salary_lpa NUMERIC(10, 2) NOT NULL,
    offer_salary_lpa NUMERIC(10, 2) NOT NULL,
    target_salary_lpa NUMERIC(10, 2) NOT NULL,
    years INTEGER NOT NULL,
    iterations INTEGER NOT NULL,
    risk_tolerance VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE simulation_runs (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES decision_scenarios(id),
    status VARCHAR(20) NOT NULL,
    summary_json TEXT NOT NULL,
    ai_insight TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_simulation_runs_scenario_id ON simulation_runs(scenario_id);
