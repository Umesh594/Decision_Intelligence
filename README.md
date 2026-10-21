# AI Decision Consequence Simulator

Production-style decision modeling platform built with Java, Spring Boot, React, PostgreSQL, Redis, Docker, Kubernetes, GitHub Actions, Jenkins, and Azure DevOps pipeline definitions.

This is not an advice chatbot. It models probabilistic futures using calibrated Monte Carlo simulation, then optionally uses Groq to generate traceable explanations from the simulation output.

## What This Proves On Your Resume

- A concurrent, convergence-driven Monte Carlo engine (`backend/src/main/java/com/umesh/decision/engine`): iterations run on a correctly-bounded `ThreadPoolExecutor` (Java 17 target, so classic executors rather than virtual threads — see `EngineExecutorConfig`), with per-batch `SplittableRandom` streams (never a shared `Random`), a scrambled Sobol low-discrepancy sequence for the dominant systematic risk factors, lock-free online variance tracking (Welford's algorithm via CAS) driving early-stop convergence detection on the goal-probability confidence interval, and thread-safe shared aggregation via `LongAdder`/`DoubleAdder` plus a concurrent bounded reservoir sampler.
- Idempotent, resumable simulation runs: a request is keyed by a client `Idempotency-Key` header or a SHA-256 content fingerprint; progress is checkpointed per decision path in Postgres (`simulation_checkpoints`) so a retry or resume never re-runs a completed batch or double-counts its contribution — see `SimulationOrchestrator` and `ResumeIdempotencyTest`.
- Java + Spring Boot backend development with deliberately-scoped transactions: the CPU-bound simulation work holds no open database connection (see `SimulationService`'s class comment for the bug this fixes).
- REST APIs, validation, security, exception handling (including a catch-all handler), OpenAPI docs
- PostgreSQL persistence with Flyway migrations
- Redis-backed idempotent-replay cache, keyed by the full idempotency key (not a lossy subset of request fields)
- Docker, Docker Compose, Kubernetes manifests
- GitHub Actions, Jenkins, Azure DevOps pipeline coverage
- Groq API integration through Groq Console key, with a deterministic simulation-only fallback when no key is configured
- Calibration layer for city cost, role level, company tier, market demand, interview conversion, emergency fund strength, skill demand and historical salary growth — externalized to `application.yml` (`app.calibration.*`) rather than hardcoded in Java, so the baseline dataset is swappable without a code change
- JIRA and Azure DevOps configuration health checks for enterprise workflow visibility (see "Known Simplifications" below for exactly what this does and doesn't do)

## Quick Start

Create a `.env` file from `.env.example`, then run:

```bash
docker compose up --build
```

Frontend: http://localhost:5173

Backend API: http://localhost:8081

Swagger UI: http://localhost:8081/swagger-ui.html

## Local Backend

If Maven is installed:

```bash
cd backend
mvn spring-boot:run
```

Required environment variables:

```bash
APP_API_KEY=local-dev-key
GROQ_API_KEY=your_groq_console_key
GROQ_MODEL=llama-3.3-70b-versatile
DATABASE_URL=jdbc:postgresql://localhost:5432/decision_simulator
DATABASE_USERNAME=decision
DATABASE_PASSWORD=decision
REDIS_HOST=localhost
```

## Example Request

```bash
curl -X POST http://localhost:8081/api/v1/simulations \
  -H "Content-Type: application/json" \
  -H "X-API-Key: local-dev-key" \
  -d '{
    "title": "Should I switch jobs?",
    "currentSalaryLpa": 8,
    "offerSalaryLpa": 12,
    "location": "Bangalore",
    "targetSalaryLpa": 25,
    "years": 4,
    "iterations": 5000,
    "riskTolerance": "MEDIUM",
    "calibration": {
      "roleLevel": "Junior",
      "companyTier": "TIER_2",
      "marketDemand": "HIGH",
      "cityCostIndex": 1.12,
      "interviewConversionRate": 0.18,
      "historicalSalaryGrowth": 0.14,
      "marketVolatility": 0.16,
      "emergencyFundScore": 0.45,
      "skillDemandScore": 0.82
    },
    "paths": [
      {
        "name": "Stay",
        "startingSalaryLpa": 8,
        "annualGrowthMean": 0.12,
        "annualGrowthStdDev": 0.07,
        "layoffProbability": 0.04,
        "promotionProbability": 0.18,
        "promotionSalaryBoost": 0.18,
        "switchProbability": 0.18,
        "switchSalaryBoost": 0.25,
        "costOfLivingAdjustment": 0.06
      },
      {
        "name": "Switch",
        "startingSalaryLpa": 12,
        "annualGrowthMean": 0.10,
        "annualGrowthStdDev": 0.08,
        "layoffProbability": 0.07,
        "promotionProbability": 0.22,
        "promotionSalaryBoost": 0.20,
        "switchProbability": 0.22,
        "switchSalaryBoost": 0.22,
        "costOfLivingAdjustment": 0.08
      }
    ]
  }'
```

## Architecture

```text
React + TypeScript
        |
Spring Boot REST API (API-key auth, global exception handling)
        |
SimulationService (idempotency + short transactions)
        |
SimulationOrchestrator ---- CalibrationService
        |
MonteCarloEngine (bounded ThreadPoolExecutor, Sobol quasi-random
  sampling, Welford convergence detection, concurrent accumulation)
        |                                   |
PostgreSQL + Flyway                  Groq Insight Service
(simulation_runs, simulation_checkpoints)   |
        |                            Redis (idempotent-replay cache)
```

## Known Simplifications

Being direct about what is, and isn't, backed by real external data or infrastructure:

- **Calibration baselines are hand-curated, not learned.** `CalibrationService`'s city-cost/role/company-tier/market-demand tables (`app.calibration.*` in `application.yml`) and `MarketIntelligenceService`'s salary bands are reasoned static datasets, not output from a trained model or a live labor-market API. They are versionable via config, and callers can override any field per request — but they are a baseline, not a model.
- **Percentiles are a bounded approximation under heavy load.** Mean, variance, goal probability, downside risk and resilience are computed from exact running sums and are never approximated. The p10/p50/p90 salary bands come from a bounded concurrent reservoir sample (default 8,000 values) rather than the full iteration history, so a 100,000-iteration run's percentiles are a well-understood statistical approximation, not exact order statistics. This trade-off exists specifically so a checkpoint's payload size doesn't grow with iteration count.
- **JIRA/Azure DevOps integration is a configuration health check, not a working write path.** `/api/v1/integrations/health` reports whether credentials are configured; there is no code path that actually creates or syncs an issue (a half-built, never-invoked `createDecisionIssue` method was removed rather than left as dead code).
- **The Groq insight is advisory text generation, not a second source of truth.** It explains the simulation's own numbers; if no API key is configured (or the call fails), a deterministic, simulation-only summary is returned instead — there is no silent fallback to fabricated advice.

## Resume Line

Built an AI Decision Consequence Simulator using Java, Spring Boot, React, PostgreSQL, Redis, Docker, Kubernetes, GitHub Actions, Jenkins and Groq, with a concurrent, convergence-driven Monte Carlo engine (bounded thread pool, quasi-random low-discrepancy sampling, lock-free online variance tracking, idempotent/resumable checkpointed runs) comparing career and financial decision paths with probabilistic goal outcomes and AI-generated traceable insights.
