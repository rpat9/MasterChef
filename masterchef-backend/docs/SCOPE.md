# Scope Document — MasterChef Backend

> **Version**: 2.0  
> **Date**: February 7, 2026  
> **Status**: Updated — JWT authentication and user management now IN SCOPE  
> **Changed from v1.0**: Added full authentication, user profiles, saved recipes

---

## 1. Project Purpose

Build a **production-ready, AI-powered recipe generation backend with JWT authentication** featuring
enterprise-level Java/Spring engineering, secure API design, cost-aware LLM orchestration, and
AWS-ready infrastructure.

**Deployment Strategy**: The system is architected for **AWS ECS deployment** with full production 
configurations. All AWS services (S3, RDS, CloudWatch, Secrets Manager) are defined via Terraform
and validated locally using LocalStack.

---

## 2. Core Features

### 2.1 Authentication & User Management (NEW in v2.0)

**JWT-based authentication** with Spring Security for secure, stateless API access.

| ID     | Requirement                                                      | Priority |
|--------|------------------------------------------------------------------|----------|
| AUTH-1 | User registration with email validation and password hashing     | P0       |
| AUTH-2 | User login with JWT token generation (access + refresh tokens)   | P0       |
| AUTH-3 | Token-based authentication for all protected endpoints           | P0       |
| AUTH-4 | Password encryption using BCrypt                                 | P0       |
| AUTH-5 | User profile management (name, email, dietary preferences)       | P0       |
| AUTH-6 | Logout functionality with token invalidation                     | P1       |
| AUTH-7 | Token refresh mechanism                                          | P1       |

### 2.2 Recipe Generation with AI

**AI-augmented recipe generation** using local LLM with cost controls and caching.

| ID    | Requirement                                                       | Priority |
|-------|-------------------------------------------------------------------|----------|
| FR-1  | Accept 3–15 ingredients and return a structured recipe            | P0       |
| FR-2  | Normalize & deduplicate ingredient input                          | P0       |
| FR-3  | Support dietary preferences (vegan, gluten-free, keto, etc.)      | P0       |
| FR-4  | Cache identical prompts (SHA-256 hash) with configurable TTL      | P0       |
| FR-5  | Track token usage and estimated cost per generation               | P0       |
| FR-6  | Return structured recipe (title, description, instructions, etc.) | P0       |
| FR-7  | Associate generated recipes with authenticated users              | P0       |

### 2.3 Recipe Management

**User-specific recipe persistence** and retrieval.

| ID    | Requirement                                                       | Priority |
|-------|-------------------------------------------------------------------|----------|
| REC-1 | Save generated recipes to user's collection                       | P0       |
| REC-2 | Retrieve all saved recipes for authenticated user                 | P0       |
| REC-3 | Remove recipes from saved collection                              | P0       |
| REC-4 | Search/filter saved recipes by ingredients or tags                | P1       |
| REC-5 | View recipe generation history with timestamps                    | P1       |
| REC-6 | Export recipes to PDF or S3 (via LocalStack)                      | P2       |

### 2.4 Observability & Metrics

| ID    | Requirement                                                       | Priority |
|-------|-------------------------------------------------------------------|----------|
| OBS-1 | Expose generation metrics (latency, cache hit rate, token spend)  | P1       |
| OBS-2 | Track user activity metrics (recipes generated, saved)            | P1       |
| OBS-3 | Health check endpoints for all dependencies                       | P0       |

### Non-Functional Requirements

| ID     | Requirement                                                      | Target        |
|--------|------------------------------------------------------------------|---------------|
| NFR-1  | API response time (cache hit)                                    | < 200 ms      |
| NFR-2  | API response time (LLM generation)                               | < 5 s         |
| NFR-3  | Test coverage                                                    | ≥ 80 %        |
| NFR-4  | Graceful degradation when LLM is unavailable                     | Circuit breaker |
| NFR-5  | Structured JSON logging with request correlation                 | MDC + SLF4J   |
| NFR-6  | Zero cloud spend                                                 | LocalStack    |

---

## 3. What We Are NOT Building

These items are **out of scope** to maintain engineering focus.

| Excluded Feature        | Rationale                                                   |
|-------------------------|-------------------------------------------------------------|
| Social features         | Ratings, comments, sharing — product features, not core     |
| Meal planning           | Different domain; calendar integration complexity           |
| Grocery list generation | Feature bloat; outside recipe generation scope              |
| Mobile native apps      | Web-first; responsive design covers mobile                  |
| Multi-model LLM ensemble| One model demonstrates the abstraction pattern              |
| Recipe photo upload     | Adds storage/moderation complexity                          |
| Email notifications     | SMTP integration outside current scope                      |
| Payment/subscription    | Monetization not relevant for this system                   |

**Note**: User authentication and saved recipes **ARE NOW IN SCOPE** (changed from v1.0).

---

## 4. Technology Decisions

| Layer            | Choice                       | Rationale                                              |
|------------------|------------------------------|--------------------------------------------------------|
| Language         | Java 21                      | Latest LTS with virtual threads, records, pattern matching |
| Framework        | Spring Boot 4.0.2            | Latest stable, production-grade framework              |
| Security         | Spring Security + JWT        | Industry standard for stateless authentication         |
| JWT Library      | jjwt 0.12.6                  | Mature, secure JWT implementation                      |
| Database         | PostgreSQL 16                | Production RDBMS with JSON support and reliability     |
| Migrations       | Flyway                       | Version-controlled, SQL-native schema evolution        |
| LLM Runtime      | Ollama (Mistral 7B)          | Free, local inference; no API keys required            |
| Object Storage   | S3 via LocalStack            | AWS SDK v2 integration with S3                         |
| Resilience       | Resilience4j                 | Circuit breaker, retry, rate limiter patterns          |
| API Docs         | Springdoc OpenAPI 3          | Auto-generated Swagger UI                              |
| Observability    | Actuator + Micrometer        | Metrics, health checks, Prometheus-compatible          |
| IaC              | Terraform                    | Defines real AWS resources for ECS deployment          |
| Containers       | Docker Compose               | Local development with PostgreSQL, Ollama, LocalStack  |

---

## 5. Architecture Boundaries

```
┌─────────────────────────────────────────────────────────┐
│                   REST API Layer                         │
│         (Controllers, JWT Auth, Validation, DTOs)        │
├─────────────────────────────────────────────────────────┤
│                   Security Layer                         │
│   Spring Security ─► JWT Filter ─► UserDetailsService   │
├─────────────────────────────────────────────────────────┤
│                   Service Layer                          │
│  AuthService  │  RecipeService ─► LlmOrchestrator       │
│  UserService  │                      │                   │
│               │                   LlmClient (SPI)        │
│               │                   CacheService           │
├─────────────────────────────────────────────────────────┤
│                 Data / Persistence                       │
│   UserRepository  │  RecipeRepository  │  S3 Storage     │
│   RecipeGenRepo   │  LlmCacheRepo                        │
├─────────────────────────────────────────────────────────┤
│                  Infrastructure                          │
│   PostgreSQL │ Ollama │ LocalStack │ Docker Compose     │
└─────────────────────────────────────────────────────────┘
```

### API Endpoints (Updated for v2.0)

**Authentication** (Public):
- `POST /api/v1/auth/register` - User registration
- `POST /api/v1/auth/login` - User login (returns JWT)
- `POST /api/v1/auth/refresh` - Refresh access token

**User Profile** (Protected):
- `GET /api/v1/users/profile` - Get current user profile
- `PUT /api/v1/users/profile` - Update user profile
- `PUT /api/v1/users/dietary-preferences` - Update dietary preferences

**Recipe Generation** (Protected):
- `POST /api/v1/recipes/generate` - Generate recipe from ingredients
- `GET /api/v1/recipes/history` - Get generation history

**Saved Recipes** (Protected):
- `GET /api/v1/recipes/saved` - Get all saved recipes
- `POST /api/v1/recipes/saved` - Save a recipe
- `DELETE /api/v1/recipes/saved/{id}` - Remove saved recipe

**Observability** (Public):
- `GET /actuator/health` - Health check
- `GET /actuator/metrics` - Metrics endpoint

---

## 6. Success Criteria

### Development Environment
- [ ] Backend starts with `docker-compose up -d && ./mvnw spring-boot:run`
- [ ] All database migrations apply successfully via Flyway
- [ ] LocalStack services (S3, CloudWatch, Secrets Manager) initialize correctly

### Authentication & Security
- [ ] User registration with password hashing works
- [ ] JWT tokens generated on login with proper expiration
- [ ] Protected endpoints reject requests without valid JWT
- [ ] Token refresh mechanism functions correctly

### Recipe Generation
- [ ] `POST /api/v1/recipes/generate` returns structured recipe
- [ ] Dietary preferences filter results appropriately
- [ ] Cache hit returns response in < 200 ms
- [ ] LLM generates coherent recipes from 3-15 ingredients

### Data Persistence
- [ ] Users can save and retrieve recipes
- [ ] Recipe history tracks all generations with timestamps
- [ ] User profiles update correctly

### Resilience & Observability
- [ ] Circuit breaker trips after 50% LLM failure rate
- [ ] Rate limiting prevents abuse (configurable per user)
- [ ] Health checks report status of all dependencies
- [ ] Metrics track: latency, cache hit rate, token usage, user activity

### Documentation & Testing
- [ ] ≥ 80% test coverage (unit + integration)
- [ ] OpenAPI docs auto-generated at `/swagger-ui.html`
- [ ] README provides clear setup instructions
- [ ] Terraform `plan` succeeds with no errors

### AWS Readiness
- [ ] All AWS SDK integrations work with LocalStack
- [ ] Terraform definitions valid for real AWS deployment
- [ ] Environment variables documented for cloud deployment
- [ ] Docker image builds successfully for ECS
