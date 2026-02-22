# Architecture — MasterChef Backend

> **Version**: 2.0  
> **Date**: February 5, 2026  
> **Last updated**: February 7, 2026

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [System Diagram](#2-system-diagram)
3. [Request Flow](#3-request-flow)
4. [Component Responsibilities](#4-component-responsibilities)
5. [Technology Decisions & Rationale](#5-technology-decisions--rationale)
6. [Package Structure](#6-package-structure)
7. [Data Model](#7-data-model)
8. [LLM Abstraction Layer](#8-llm-abstraction-layer)
9. [Resilience Strategy](#9-resilience-strategy)
10. [Observability](#10-observability)
11. [Infrastructure](#11-infrastructure)
12. [Security Considerations](#12-security-considerations)

---

## 1. System Overview

MasterChef Backend is a **production-grade, AI-augmented recipe generation service** with JWT authentication and cost-aware LLM orchestration. Users register, authenticate, and submit ingredients via a REST API. The system orchestrates a local Large Language Model (Ollama / Mistral 7B) to return structured recipes — while caching duplicate requests, enforcing rate limits, tracking token costs, and recording generation metrics.

The system is designed for **AWS ECS deployment** and runs locally using Docker Compose + LocalStack.

### Key Design Principles

- **Local-First**: Everything runs on `docker-compose up` with no cloud accounts
- **Cost-Aware**: Every LLM call is cached, metered, and rate-limited
- **Production-Grade**: Circuit breakers, retries, structured logging, health checks
- **Pluggable**: LLM clients implement a common interface — swap models without touching business logic
- **Observable**: Custom Micrometer metrics, Actuator endpoints, correlated logs

---

## 2. System Diagram

```
                          ┌──────────────┐
                          │   Client     │
                          │  (Browser /  │
                          │   Postman)   │
                          └──────┬───────┘
                                 │  HTTPS
                                 ▼
                ┌────────────────────────────────┐
                │         Spring Boot App         │
                │         (port 8080)             │
                │                                 │
                │  ┌───────────────────────────┐  │
                │  │    RecipeController        │  │
                │  │   /api/v1/recipes/*        │  │
                │  └─────────┬─────────────────┘  │
                │            │                     │
                │  ┌─────────▼─────────────────┐  │
                │  │     RecipeService          │  │
                │  │  - Input validation        │  │
                │  │  - Prompt construction     │  │
                │  │  - Response parsing        │  │
                │  └─────────┬─────────────────┘  │
                │            │                     │
                │  ┌─────────▼─────────────────┐  │
                │  │    LlmOrchestrator         │  │
                │  │  - Cache check (SHA-256)   │  │
                │  │  - Client selection        │  │
                │  │  - Fallback routing        │  │
                │  │  - Metrics recording       │  │
                │  └───┬───────────┬───────────┘  │
                │      │           │               │
                │      ▼           ▼               │
                │  ┌────────┐ ┌────────────────┐  │
                │  │ Cache  │ │  LlmClient     │  │
                │  │ (DB)   │ │  (Interface)   │  │
                │  └────────┘ └───┬────────────┘  │
                │                 │                │
                │      ┌──────────┤                │
                │      ▼          ▼                │
                │  ┌────────┐ ┌────────────────┐  │
                │  │Ollama  │ │ MockLlmClient  │  │
                │  │Client  │ │ (Testing)      │  │
                │  └────┬───┘ └────────────────┘  │
                │       │                          │
                │  ┌────▼──────────────────────┐  │
                │  │   JPA Repositories         │  │
                │  │   (PostgreSQL)              │  │
                │  └────┬──────────────────────┘  │
                │       │                          │
                │  ┌────▼──────────────────────┐  │
                │  │   StorageService (S3)      │  │
                │  └───────────────────────────┘  │
                └──────────────────────────────────┘
                       │            │          │
              ┌────────┘            │          └──────────┐
              ▼                     ▼                     ▼
     ┌─────────────┐      ┌──────────────┐      ┌──────────────┐
     │ PostgreSQL   │      │   Ollama     │      │  LocalStack  │
     │ (port 5432)  │      │ (port 11434) │      │ (port 4566)  │
     │              │      │  Mistral 7B  │      │ S3, CW Logs, │
     │              │      │              │      │ Secrets Mgr  │
     └─────────────┘      └──────────────┘      └──────────────┘
              ▲                                          ▲
              └──────────── Docker Compose ──────────────┘
```

---

## 3. Request Flow

### Recipe Generation (Happy Path)

```
1.  Client  ──POST /api/v1/recipes/generate──►  RecipeController
2.  RecipeController  ──validates @RequestBody──►  RecipeRequest (JSR-380)
3.  RecipeController  ──delegates──►  RecipeService.generateRecipe()
4.  RecipeService
      a.  Normalizes ingredients (lowercase, trim, deduplicate)
      b.  Builds structured prompt with constraints
      c.  Calls LlmOrchestrator.generateWithFallback()
5.  LlmOrchestrator
      a.  Computes SHA-256 hash of normalized prompt
      b.  Checks LlmCache table  ──hit?──►  return cached response
      c.  Checks circuit breaker state  ──open?──►  throw LlmUnavailableException
      d.  Calls OllamaClient.generate()
      e.  Records metrics: latency, tokens, model used
      f.  Saves response in LlmCache
      g.  Returns LlmResponse
6.  RecipeService
      a.  Parses LLM response into structured Recipe
      b.  Validates parsed output (fallback for malformed JSON)
      c.  Persists RecipeGeneration record (audit trail)
      d.  Returns RecipeResponse DTO
7.  RecipeController  ──200 OK──►  Client
```

### Error Scenarios

| Scenario                    | Handling                                             |
|-----------------------------|------------------------------------------------------|
| Invalid input (< 3 or > 15 ingredients) | `400 Bad Request` via JSR-380 validation  |
| LLM timeout                 | Retry up to 3× with exponential backoff              |
| LLM circuit open            | `503 Service Unavailable` with Retry-After header    |
| Rate limit exceeded         | `429 Too Many Requests`                              |
| Malformed LLM response      | Fallback parsing, log warning, return partial recipe |
| Database error              | `500 Internal Server Error`, structured log          |

---

## 4. Component Responsibilities

### Controller Layer (`controller/`)
| Component              | Responsibility                                    |
|------------------------|---------------------------------------------------|
| `AuthController`       | REST endpoints for registration, login, token refresh |
| `UserController`       | REST endpoints for user profile management        |
| `RecipeController`     | REST endpoints for recipe generation & history    |
| `GlobalExceptionHandler` | Maps exceptions to consistent error responses   |

### Service Layer (`service/`)
| Component              | Responsibility                                    |
|------------------------|---------------------------------------------------|
| `AuthService`          | User registration, login, JWT token generation    |
| `UserService`          | User profile, dietary preferences management      |
| `RecipeService`        | Business logic: validate, prompt, parse, persist  |
| `LlmOrchestrator`     | Cache-aware LLM routing with fallback chains      |
| `LlmCacheService`     | SHA-256 hashing, TTL management, cache CRUD       |
| `StorageService`       | S3 operations (recipe export, presigned URLs)     |

### LLM Client Layer (SPI pattern)
| Component              | Responsibility                                    |
|------------------------|---------------------------------------------------|
| `LlmClient` (interface)| Contract for all LLM integrations                |
| `OllamaClient`        | HTTP client for local Ollama instance             |
| `MockLlmClient`       | Deterministic responses for unit/integration tests|

### Data Layer (`repository/`, `model/`)
| Component              | Responsibility                                    |
|------------------------|---------------------------------------------------|
| `UserRepository`       | User CRUD                                         |
| `RecipeRepository`     | Recipe CRUD + user-scoped queries                 |
| `RecipeGenerationRepository` | Generation audit trail queries              |
| `LlmCacheRepository`  | Cache lookup by input hash, TTL expiry cleanup    |

### Cross-Cutting (`config/`, `exception/`, `security/`)
| Component              | Responsibility                                    |
|------------------------|---------------------------------------------------|
| `SecurityConfig`       | Spring Security configuration, JWT filter chain   |
| `JwtTokenProvider`     | JWT generation, validation, claims extraction     |
| `JwtAuthenticationFilter` | Request authentication via JWT tokens          |
| `AwsConfig`            | S3, CloudWatch Logs, Secrets Manager beans with LocalStack endpoint |
| `ResilienceConfig`     | Circuit breaker, retry, rate limiter setup         |
| `WebConfig`            | CORS, request logging                              |
| `LlmUnavailableException` | Typed exception for circuit-open state         |
| `RateLimitExceededException` | Typed exception for rate limit violations    |

---

## 5. Technology Decisions & Rationale

### Why Java 21?

- **Virtual Threads** (Project Loom) — prepared for high-throughput LLM calls without thread pool exhaustion
- **Record classes** — immutable DTOs with zero boilerplate
- **Pattern matching** — cleaner response parsing logic

### Why Spring Boot 4.0.2?

- Latest stable release with full Java 21 support
- Mature ecosystem: JPA, Actuator, Validation, Web MVC
- Opinionated defaults reduce configuration
- Industry standard with mature ecosystem

### Why PostgreSQL over H2/SQLite?

- Production-grade RDBMS used in real systems
- JSONB support for flexible recipe content storage
- Flyway integration is battle-tested
- H2 reserved for test profile only

### Why Ollama (Local LLM) over OpenAI/Anthropic API?

- **Zero per-request cost** — no API keys, no billing, no rate limits from provider
- `LlmClient` interface means swapping to OpenAI is a single implementation class
- Reproducible — runs identically on any machine with Docker
- Privacy — no data leaves the host machine

### Why Flyway?

- SQL-native migrations (plain `.sql` files) — transparent, auditable
- Simpler mental model for a project of this scope
- Well-integrated with Spring Boot auto-configuration

### Why Resilience4j?

- Hystrix is in maintenance mode (Netflix deprecated it)
- Resilience4j is lightweight, functional, and Spring Boot 3+ native
- Provides circuit breaker + retry + rate limiter in one library
- Annotation-driven — minimal boilerplate

### Why LocalStack over Real AWS?

- Emulates S3, CloudWatch Logs, Secrets Manager locally
- AWS SDK v2 code is identical whether targeting LocalStack or real AWS
- Terraform definitions validate against the real provider
- Enables full integration testing without cloud credentials

---

## 6. Package Structure

```
src/main/java/com/masterchef/masterchef_backend/
├── MasterchefBackendApplication.java    # Entry point
├── config/                              # Spring configuration beans
│   ├── AwsConfig.java                   # S3Client with LocalStack endpoint
│   ├── ResilienceConfig.java            # Circuit breaker, retry, rate limiter
│   └── WebConfig.java                   # CORS, request logging
├── controller/                          # REST API layer
│   ├── RecipeController.java            # /api/v1/recipes endpoints
│   └── GlobalExceptionHandler.java      # @RestControllerAdvice
├── service/                             # Business logic
│   ├── RecipeService.java               # Core recipe generation logic
│   ├── LlmOrchestrator.java             # Cache-aware LLM routing
│   ├── LlmCacheService.java             # Cache management
│   └── StorageService.java              # S3 operations
├── repository/                          # Data access (Spring Data JPA)
│   ├── UserRepository.java
│   ├── RecipeRepository.java
│   ├── RecipeGenerationRepository.java
│   └── LlmCacheRepository.java
├── model/                               # JPA entities
│   ├── User.java
│   ├── Recipe.java
│   ├── RecipeGeneration.java
│   └── LlmCache.java
├── dto/                                 # Request/response objects
│   ├── RecipeRequest.java
│   ├── RecipeResponse.java
│   ├── GenerationMetrics.java
│   ├── ErrorResponse.java
│   └── LlmResponse.java
└── exception/                           # Custom exceptions
    ├── LlmUnavailableException.java
    ├── RateLimitExceededException.java
    └── RecipeParsingException.java
```

---

## 7. Data Model

### Entity Relationship Diagram

```
┌───────────────────────┐       1:N       ┌─────────────────────────┐
│        users          │────────────────►│        recipes          │
│                       │                 │                         │
│ id          UUID (PK) │                 │ id          UUID (PK)   │
│ name        VARCHAR   │       1:N       │ user_id     UUID (FK)   │
│ email       VARCHAR   │───────┐         │ title       VARCHAR     │
│ created_at  TIMESTAMP │       │         │ description TEXT        │
│ updated_at  TIMESTAMP │       │         │ content     TEXT        │
└───────────────────────┘       │         │ ingredients TEXT[]      │
                                │         │ cuisine     VARCHAR     │
                                │         │ prep_time   INTEGER     │
                                │         │ servings    INTEGER     │
                                │         │ created_at  TIMESTAMP   │
                                │         │ updated_at  TIMESTAMP   │
                                │         └─────────────────────────┘
                                │
                                │         ┌─────────────────────────┐
                                └────────►│  recipe_generations     │
                                          │                         │
                                          │ id          UUID (PK)   │
                                          │ user_id     UUID (FK)   │
                                          │ ingredients TEXT[]      │
                                          │ prompt      TEXT        │
                                          │ raw_response TEXT       │
                                          │ model_used  VARCHAR     │
                                          │ tokens_used INTEGER     │
                                          │ cost_cents  INTEGER     │
                                          │ cached      BOOLEAN     │
                                          │ latency_ms  BIGINT      │
                                          │ status      VARCHAR     │
                                          │ created_at  TIMESTAMP   │
                                          └─────────────────────────┘

┌─────────────────────────┐
│       llm_cache         │  (independent — no FK)
│                         │
│ id          UUID (PK)   │
│ input_hash  VARCHAR (UQ)│  SHA-256 of normalized prompt
│ response    TEXT         │
│ model       VARCHAR     │
│ tokens_used INTEGER     │
│ created_at  TIMESTAMP   │
│ expires_at  TIMESTAMP   │
└─────────────────────────┘
```

### Design Notes

- **UUIDs as primary keys** — avoids sequential ID enumeration, safe for
  external exposure in API responses
- `llm_cache` is intentionally **not** foreign-keyed to any table — it's a
  content-addressable store keyed by input hash
- `recipe_generations` is the **audit trail** — every LLM call (including cache
  hits) gets a record with the raw prompt, raw response, and cost tracking
- `recipes` stores the **parsed, structured output** for user-facing history
- `content` column uses PostgreSQL `TEXT` to store the full recipe body
- `ingredients` columns use PostgreSQL `TEXT[]` array type for native array queries
- `status` in `recipe_generations` tracks `SUCCESS`, `CACHE_HIT`, `FAILED`,
  `TIMEOUT` for operational visibility

---

## 8. LLM Abstraction Layer

This is the most architecturally significant component. The design follows the
**Strategy + Facade pattern**:

```
                         ┌──────────────────┐
                         │  RecipeService   │
                         └────────┬─────────┘
                                  │
                         ┌────────▼─────────┐
                         │ LlmOrchestrator  │  ◄── Facade
                         │                  │
                         │ 1. Check cache   │
                         │ 2. Select client │
                         │ 3. Call LLM      │
                         │ 4. Record metrics│
                         │ 5. Cache response│
                         └──┬──────────┬────┘
                            │          │
                   ┌────────▼──┐  ┌────▼───────────┐
                   │OllamaClient│  │MockLlmClient  │
                   │           │  │(test profile)  │
                   └─────┬─────┘  └────────────────┘
                         │
                         │  HTTP POST
                         ▼
                  ┌──────────────┐
                  │    Ollama    │
                  │  Mistral 7B  │
                  └──────────────┘
```

### Interface Contract

```java
public interface LlmClient {
    LlmResponse generate(PromptRequest request);
    boolean isAvailable();        // Health check
    String getModelName();        // For metrics tagging
    int estimateTokens(String text);  // Cost estimation
}
```

### Why This Design?

1. **Testability** — `MockLlmClient` returns deterministic responses; no Ollama
   needed for CI
2. **Extensibility** — Adding OpenAI support = one new class implementing `LlmClient`
3. **Observability** — `LlmOrchestrator` is the single place to add metrics,
   logging, and circuit breakers
4. **Cost control** — Caching and rate limiting live in the orchestrator, not
   scattered across clients

---

## 9. Resilience Strategy

```
Request ──► RateLimiter ──► CircuitBreaker ──► Retry ──► LlmClient
              │                  │               │
              ▼                  ▼               ▼
          429 Error          503 Error       Exponential
          (Too Many          (Service        Backoff
           Requests)         Unavailable)    (2s, 4s, 8s)
```

| Pattern          | Config                          | Purpose                              |
|------------------|---------------------------------|--------------------------------------|
| **Rate Limiter** | 10 req/min per user             | Prevent abuse, control LLM costs     |
| **Circuit Breaker** | Opens at 50% failure, 30s wait | Stop hammering a dead Ollama instance |
| **Retry**        | 3 attempts, exponential backoff | Handle transient network issues       |
| **Timeout**      | 30s per LLM call                | Prevent thread starvation             |
| **Fallback**     | Cached response or error DTO    | Always return something meaningful    |

---

## 10. Observability

### Metrics (Micrometer → Actuator `/metrics`)

| Metric Name                              | Type      | Tags            |
|------------------------------------------|-----------|-----------------|
| `masterchef.recipe.generation.duration`  | Timer     | model, cached   |
| `masterchef.llm.call.duration`           | Timer     | model, status   |
| `masterchef.llm.cache.hits`             | Counter   | —               |
| `masterchef.llm.cache.misses`           | Counter   | —               |
| `masterchef.llm.tokens.used`            | Counter   | model           |
| `masterchef.recipe.generation.errors`    | Counter   | error_type      |

### Structured Logging

Every log line includes MDC context:

```
2026-02-05 14:23:01 [http-nio-8080-exec-1] INFO  RecipeService
  userId=abc-123 ingredientCount=5 action=generate_recipe status=success
  latencyMs=2341 model=mistral cached=false tokensUsed=847
```

### Health Checks (`/actuator/health`)

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "ollama": { "status": "UP", "details": { "model": "mistral" } },
    "s3": { "status": "UP", "details": { "bucket": "masterchef-recipes" } },
    "circuitBreaker": { "status": "UP", "details": { "state": "CLOSED" } }
  }
}
```

---

## 11. Infrastructure

### Local Development (Docker Compose)

```
┌────────────────────────────────────────────────┐
│              Docker Compose Network             │
│                                                 │
│  ┌────────────┐  ┌──────────┐  ┌────────────┐ │
│  │ PostgreSQL  │  │  Ollama  │  │ LocalStack │ │
│  │  :5432      │  │  :11434  │  │  :4566     │ │
│  └────────────┘  └──────────┘  └────────────┘ │
└────────────────────────────────────────────────┘
         ▲               ▲              ▲
         │               │              │
         └───────────────┼──────────────┘
                         │
              ┌──────────┴──────────┐
              │  Spring Boot App    │
              │  (host, port 8080)  │
              └─────────────────────┘
```

### Production Target (AWS — via Terraform)

```
┌─────────────────────────────────────────────────────────┐
│                         AWS VPC                          │
│                                                          │
│  ┌──────────────┐   ┌──────────────┐   ┌─────────────┐ │
│  │   ALB        │──►│  ECS Fargate │──►│  RDS         │ │
│  │  (HTTPS)     │   │  (256 CPU    │   │  PostgreSQL  │ │
│  └──────────────┘   │   512 MB)    │   │  (db.t3.micro│ │
│                      └──────┬───────┘   └─────────────┘ │
│                             │                            │
│                      ┌──────▼───────┐   ┌─────────────┐ │
│                      │  CloudWatch  │   │  S3 Bucket  │ │
│                      │  Logs +      │   │  (recipes)  │ │
│                      │  Metrics     │   └─────────────┘ │
│                      └──────────────┘                    │
│                                         ┌─────────────┐ │
│                                         │  Secrets    │ │
│                                         │  Manager    │ │
│                                         │  (DB creds) │ │
│                                         └─────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### Estimated AWS Cost

Depends on traffic, requests, uptime, etc. Up to you for deployment!

## 12. Security Considerations

The system implements production-grade security practices:

| Concern                | Mitigation                                         |
|------------------------|----------------------------------------------------|
| Authentication         | JWT tokens with 15-min access, 7-day refresh tokens |
| Password security      | BCrypt hashing with cost factor 12                 |
| Token validation       | Spring Security filter chain validates all requests |
| Authorization          | User-scoped resources (recipes, preferences)       |
| Input injection        | JSR-380 validation, ingredient allowlist pattern   |
| LLM prompt injection   | Structured prompt template, output sanitization    |
| SQL injection          | Spring Data JPA parameterized queries              |
| Resource exhaustion    | Rate limiter, circuit breaker, request timeouts    |
| Sensitive data in logs | No PII logged; MDC uses opaque user IDs            |
| AWS credential leaks   | LocalStack uses dummy credentials, no real keys    |
| Dependency vulnerabilities | `mvn dependency:check` in CI pipeline           |

---

## Appendix: ADR Log

### ADR-001: Use Local LLM Instead of Cloud API
- **Status**: Accepted
- **Context**: Cloud LLM APIs (OpenAI, Anthropic) incur per-request costs and require API keys
- **Decision**: Use Ollama with Mistral 7B locally, behind an interface
- **Consequence**: Slightly lower output quality; zero per-request cost; fully reproducible

### ADR-002: PostgreSQL Cache Instead of Redis
- **Status**: Accepted  
- **Context**: Redis would add another infrastructure dependency
- **Decision**: Use a `llm_cache` table in PostgreSQL with TTL-based expiry
- **Consequence**: Slightly slower cache reads; one fewer container to manage; simpler local setup

### ADR-003: JWT Authentication (Updated v2.0)
- **Status**: Accepted (Scope expanded from v1.0)
- **Context**: Authentication is required for user-specific features (saved recipes, preferences) and secure API access
- **Decision**: Implement JWT-based authentication with Spring Security
  - Access tokens: 15-minute expiration
  - Refresh tokens: 7-day expiration
  - BCrypt password hashing (cost factor 12)
  - Stateless authentication (no server-side sessions)
- **Consequence**: Adds security layer complexity; enables user-scoped features