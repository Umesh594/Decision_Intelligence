# AI Decision Consequence Simulator

Production-style decision modeling platform built with Java, Spring Boot, React, PostgreSQL, Redis, Docker, Kubernetes, GitHub Actions, Jenkins, and Azure DevOps pipeline definitions.

This is not an advice chatbot. It models probabilistic futures using calibrated Monte Carlo simulation, then optionally uses Groq to generate traceable explanations from the simulation output.

## What This Proves On Your Resume

- Java + Spring Boot backend development
- REST APIs, validation, security, exception handling, OpenAPI docs
- PostgreSQL persistence with Flyway migrations
- Redis-ready caching
- Message/async style execution with Spring async services
- Docker, Docker Compose, Kubernetes manifests
- GitHub Actions, Jenkins, Azure DevOps pipeline coverage
- Groq API integration through Groq Console key
- Calibration layer for city cost, role level, company tier, market demand, interview conversion, emergency fund strength, skill demand and historical salary growth
- JIRA and Azure DevOps integration placeholders for enterprise workflows

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
Spring Boot REST API
        |
Simulation Service ---- Calibration Service ---- Groq Insight Service
        |
PostgreSQL + Flyway
        |
Redis cache / async-ready workers
```

## Resume Line

Built an AI Decision Consequence Simulator using Java, Spring Boot, React, PostgreSQL, Redis, Docker, Kubernetes, GitHub Actions, Jenkins and Groq, running calibrated Monte Carlo simulations across city cost, company tier, market demand, interview conversion and historical growth to compare career and financial decision paths with probabilistic goal outcomes and AI-generated traceable insights.
