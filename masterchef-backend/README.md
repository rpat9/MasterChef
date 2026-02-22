# MasterChef Backend

> **Production-ready AI-powered recipe generation service** with JWT authentication, cost-aware LLM orchestration, and AWS-native architecture

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](../LICENSE)

---

## 📋 Table of Contents

- [Overview](#overview)
- [Why This Architecture?](#why-this-architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [API Documentation](#api-documentation)
- [AWS Deployment](#aws-deployment)
- [Architecture Deep Dive](#architecture-deep-dive)
- [Development](#development)

---

## 🎯 Overview

MasterChef Backend is a **Spring Boot REST API** that generates AI-powered recipes using local LLM inference (Ollama/Mistral 7B), with JWT authentication and PostgreSQL persistence. Built with production-grade patterns while maintaining zero cloud costs during development.

### What's Working Now

- 🔐 **JWT Authentication** - User registration, login, token refresh with Spring Security
- 🤖 **AI Recipe Generation** - Local LLM inference with Mistral 7B (no API costs)
- 📊 **Audit Trail** - Complete tracking of LLM requests, responses, and performance metrics
- 🗄️ **Database Persistence** - PostgreSQL with Flyway migrations for schema management
- ✅ **Testing** - End-to-end test suite validating the complete flow

### Core Architecture

- **Java 21** + **Spring Boot 4.0.2** for modern enterprise Java development
- **PostgreSQL 16** for reliable data persistence
- **Ollama** for local AI inference (Mistral 7B, ~4.4GB model)
- **Docker Compose** for reproducible local development environment
- **Stateless JWT** authentication (15-minute access tokens, 7-day refresh tokens)

---

## 🏗️ Why This Architecture?

### Design Philosophy

1. **Production Patterns** - Enterprise-grade security, database migrations, structured logging, and audit trails
2. **Zero-Cost AI** - Local LLM inference with Ollama eliminates API costs while maintaining quality
3. **Cloud-Ready** - Designed for AWS deployment but runs entirely locally during development
4. **Simple First** - Core functionality built solidly before adding complexity like caching and resilience patterns

### Current Focus

The system prioritizes **core functionality done right**:
- Secure authentication with industry-standard JWT
- Real AI recipe generation with structured output
- Complete audit trail for debugging and metrics
- Reproducible local environment with Docker

### Why Not Deployed?

This system is **deployment-ready** for AWS (designed for ECS, RDS, and S3). The architecture supports production deployment to any cloud provider. 

**The maintainer chooses not to deploy to avoid recurring costs** (~$50-100/month for minimal traffic). This demonstrates production-ready engineering practices while maintaining zero operational expenses.

**Translation**: *"I know how to deploy to production. I'm choosing not to pay for it."*

---

## 🚀 Tech Stack

### Core Technologies

| Component            | Technology               | Purpose                                    |
|----------------------|--------------------------|--------------------------------------------|
| **Language**         | Java 21                  | Modern Java with virtual threads, records  |
| **Framework**        | Spring Boot 4.0.2        | Enterprise web application framework       |
| **Security**         | Spring Security + JWT    | Stateless authentication and authorization |
| **Database**         | PostgreSQL 16            | Primary data store                         |
| **Migrations**       | Flyway                   | Version-controlled schema evolution        |
| **Password Hashing** | BCrypt                   | Secure password storage                    |

### AI & Integration

| Component            | Technology               | Purpose                                    |
|----------------------|--------------------------|--------------------------------------------|
| **LLM Runtime**      | Ollama (Mistral 7B)      | Local AI inference (zero API costs)        |
| **HTTP Client**      | RestTemplate             | Communication with Ollama API              |
| **JSON Processing**  | Jackson ObjectMapper     | Recipe parsing and serialization           |

### Infrastructure

| Component            | Technology               | Purpose                                    |
|----------------------|--------------------------|--------------------------------------------|
| **Containerization** | Docker + Docker Compose  | Local development environment              |
| **Database Driver**  | PostgreSQL JDBC          | Database connectivity                      |
| **Connection Pool**  | HikariCP                 | Database connection pooling                |

### Observability

| Component            | Technology               | Purpose                                    |
|----------------------|--------------------------|--------------------------------------------|
| **Health Checks**    | Spring Actuator          | `/actuator/health` endpoints               |
| **Logging**          | SLF4J + Logback          | Structured application logging             |
| **Audit Trail**      | Database tables          | LLM request/response tracking              |

### Development Tools

| Component            | Technology               | Purpose                                    |
|----------------------|--------------------------|--------------------------------------------|
| **Build Tool**       | Maven                    | Dependency management and build automation |
| **Testing**          | Bash scripts             | End-to-end integration testing             |
| **Code Quality**     | Lombok                   | Boilerplate reduction                      |

---

## ✨ Features

### ✅ Currently Implemented

#### Authentication & Security
- User registration with email validation
- Secure password hashing (BCrypt, cost factor 12)
- JWT token generation (access + refresh tokens)
- Token-based API authentication with Spring Security
- Access tokens: 15-minute expiration
- Refresh tokens: 7-day expiration

#### Recipe Generation
- AI-powered recipe creation from ingredients using local LLM (Ollama/Mistral 7B)
- Dietary preference support (vegan, gluten-free, keto, etc.)
- Structured recipe output (title, description, instructions, nutrition, ingredients)
- Generation history tracking with full audit trail
- Token usage and latency metrics per generation
- Configurable serving sizes and difficulty levels

#### Observability
- Health check endpoints (Spring Actuator)
- Structured logging with request correlation
- Database connection monitoring

### 🚧 Planned Features

#### Smart Caching
- SHA-256 content-addressable caching for LLM responses
- Cache hit rate tracking
- TTL-based cache expiration

#### Recipe Management
- Save/unsave recipes to personal collection
- Retrieve saved recipes
- Search and filter by ingredients or tags
- Export recipes to S3

#### Resilience & Reliability
- Circuit breaker for LLM failures
- Retry logic with exponential backoff
- Rate limiting per user
- Request timeouts

#### AWS Integration
- S3 for recipe exports
- CloudWatch for logs and metrics
- Secrets Manager for credentials
- ECS Fargate deployment

---

## 🏁 Getting Started

### Prerequisites

- **Java 21**
- **Docker & Docker Compose** - [Download](https://www.docker.com/products/docker-desktop)
- **Maven 3.9+**

### Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/rpat9/MasterChef.git
cd MasterChef/masterchef-backend

# 2. Start infrastructure (PostgreSQL, Ollama)
docker-compose up -d

# 3. Wait for Ollama to pull Mistral model (first time only, ~5 minutes)
docker-compose logs -f ollama
# Look for: "success"

# 4. Run database migrations
./mvnw flyway:migrate

# 5. Start the application
./mvnw spring-boot:run

# 6. Verify health
curl http://localhost:8080/actuator/health
```

The API will be available at `http://localhost:8080`

### Running the Test Suite

```bash
# Run end-to-end test script
./test-recipe-generation.sh
```

This script tests:
- ✓ JWT authentication (registration and login)
- ✓ Protected endpoint authorization
- ✓ Recipe generation with Ollama
- ✓ Database persistence
- ✓ Response metadata (tokens, latency)

---

## 📁 Project Structure

```
masterchef-backend/
├── src/
│   ├── main/
│   │   ├── java/com/masterchef/masterchef_backend/
│   │   │   ├── config/              # Configuration beans
│   │   │   │   ├── SecurityConfig.java        # Spring Security + JWT
│   │   │   │   └── RestTemplateConfig.java    # HTTP client config
│   │   │   ├── controller/          # REST API endpoints
│   │   │   │   ├── AuthController.java        # /api/v1/auth/*
│   │   │   │   └── RecipeController.java      # /api/v1/recipes/*
│   │   │   ├── service/             # Business logic
│   │   │   │   ├── AuthService.java           # Registration, login, token refresh
│   │   │   │   └── RecipeService.java         # Recipe generation orchestration
│   │   │   ├── security/            # JWT & Authentication
│   │   │   │   ├── JwtTokenProvider.java      # Token generation/validation
│   │   │   │   ├── JwtAuthenticationFilter.java  # Request authentication
│   │   │   │   └── UserDetailsServiceImpl.java   # User loading
│   │   │   ├── repository/          # Data access (JPA)
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── RecipeRepository.java
│   │   │   │   └── RecipeGenerationRepository.java
│   │   │   ├── models/              # JPA entities
│   │   │   │   ├── User.java
│   │   │   │   ├── Recipe.java
│   │   │   │   └── RecipeGeneration.java
│   │   │   ├── dto/                 # Request/response objects
│   │   │   │   ├── LoginRequest.java
│   │   │   │   ├── RegisterRequest.java
│   │   │   │   ├── AuthResponse.java
│   │   │   │   ├── TokenRefreshRequest.java
│   │   │   │   ├── TokenRefreshResponse.java
│   │   │   │   ├── RecipeRequest.java
│   │   │   │   ├── RecipeResponse.java
│   │   │   │   ├── LlmRequest.java
│   │   │   │   └── LlmResponse.java
│   │   │   └── llm/                 # LLM abstraction
│   │   │       ├── LlmClient.java (interface)
│   │   │       └── OllamaClient.java
│   │   └── resources/
│   │       ├── application.yml               # Main configuration
│   │       └── db/migration/                 # Flyway SQL scripts
│   │           └── V1__create_initial_schema.sql
│   └── test/                        # Tests (to be implemented)
├── docs/
│   ├── ARCHITECTURE.md              # System design deep dive
│   └── SCOPE.md                     # Feature scope document
├── docker-compose.yml               # Local infrastructure stack
├── test-recipe-generation.sh        # E2E test script
├── pom.xml                          # Maven dependencies
└── README.md                        # This file
```

---

## 📖 API Documentation

### Base URL

```
http://localhost:8080/api/v1
```

### Endpoints

#### Authentication — `/api/v1/auth`

| Method | Endpoint               | Description                         | Auth Required |
|--------|------------------------|-------------------------------------|---------------|
| POST   | `/auth/register`       | Register a new user                 | No            |
| POST   | `/auth/login`          | Login and receive JWT tokens        | No            |
| POST   | `/auth/refresh`        | Exchange refresh token for new access token | No   |

#### Recipes — `/api/v1/recipes`

| Method | Endpoint                  | Description                                          | Auth Required |
|--------|---------------------------|------------------------------------------------------|---------------|
| POST   | `/recipes/generate`       | Generate a recipe from a list of ingredients         | Yes           |
| GET    | `/recipes`                | Get all saved recipes for the authenticated user (paginated) | Yes  |
| GET    | `/recipes/{id}`           | Get a specific recipe by ID                          | Yes           |
| DELETE | `/recipes/{id}`           | Delete a recipe and its S3 exports                   | Yes           |
| POST   | `/recipes/{id}/export`    | Export a recipe to S3 and get a presigned download URL | Yes         |
| GET    | `/recipes/history`        | Get LLM generation history for the authenticated user (paginated) | Yes |
| GET    | `/recipes/metrics`        | Get personal usage metrics (generations, cache hits, token totals) | Yes |

#### Admin — `/api/v1/admin`

| Method | Endpoint                  | Description                                | Auth Required |
|--------|---------------------------|--------------------------------------------|---------------|
| GET    | `/admin/cache/stats`      | Get LLM cache statistics (hit rate, totals) | Yes (Admin)  |
| DELETE | `/admin/cache`            | Delete all expired LLM cache entries       | Yes (Admin)   |

#### Health & Monitoring

| Method | Endpoint            | Description               | Auth Required |
|--------|---------------------|---------------------------|---------------|
| GET    | `/actuator/health`  | Application health status | No            |
| GET    | `/actuator/info`    | Application information   | No            |

### Example: Register User

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com",
    "password": "securepass123"
  }'
```

**Response:**
```json
{
  "userId": "8d99355d-3294-4035-b9fe-bdf58b813c14",
  "email": "john@example.com",
  "name": "John Doe",
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "issuedAt": "2026-02-09T17:30:00Z"
}
```

### Example: Generate Recipe

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/recipes/generate \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ingredients": ["chicken", "garlic", "olive oil", "lemon", "thyme"],
    "dietaryPreferences": ["gluten-free"],
    "servings": 4,
    "difficulty": "easy",
    "maxTimeMinutes": 45
  }'
```

**Response:**
```json
{
  "id": "82c09abf-4687-4f5e-81fa-85343f4b3ae3",
  "title": "Lemon Thyme Chicken",
  "description": "A simple and delicious recipe using chicken, garlic, olive oil, lemon, and thyme.",
  "prepTime": 15,
  "cookTime": 30,
  "totalTime": 45,
  "servings": 4,
  "difficulty": "easy",
  "cuisine": "Italian",
  "ingredientsUsed": ["chicken", "garlic", "olive oil", "lemon", "thyme"],
  "instructions": "[\"Step 1: Preheat the oven...\", \"Step 2: Season the chicken...\"]",
  "ingredients": "[{\"name\":\"chicken\",\"amount\":\"500\",\"unit\":\"g\"}]",
  "nutritionInfo": "{\"calories\":350,\"protein\":30,\"carbs\":20,\"fat\":15}",
  "tags": ["Quick", "Healthy"],
  "isSaved": true,
  "createdAt": "2026-02-09T17:34:19Z",
  "metadata": {
    "model": "mistral",
    "tokensUsed": 363,
    "latencyMs": 19435,
    "cached": false,
    "generatedAt": "2026-02-09T17:34:19Z"
  }
}
```

---

## ☁️ AWS Deployment (Planned)

### Target Architecture

The system is designed for **AWS ECS (Fargate)** deployment with:

- **ECS Fargate** - Containerized Spring Boot application
- **Application Load Balancer** - HTTPS termination and routing
- **RDS PostgreSQL** - Managed database
- **S3** - Recipe exports and static assets
- **CloudWatch** - Logs and metrics
- **Secrets Manager** - Secure credential storage

### Estimated Monthly Cost

| Service          | Configuration    | Monthly Cost (Estimate) |
|------------------|------------------|-------------------------|
| ECS Fargate      | 256 CPU, 512 MB  | ~$15                    |
| RDS (PostgreSQL) | db.t3.micro      | ~$15                    |
| ALB              | Minimal traffic  | ~$20                    |
| S3               | <1 GB storage    | ~$1                     |
| CloudWatch       | Logs + metrics   | ~$5                     |
| **Total**        |                  | **~$56/month**          |

*Note: This is deployment-ready infrastructure that anyone can deploy to their own AWS account. The maintainer chooses not to deploy to avoid recurring costs, demonstrating zero-cost local development while maintaining production-ready code quality.*

---

## 🛠️ Architecture Deep Dive

### Current Implementation

#### LLM Integration

The system uses **Ollama** for local LLM inference with the Mistral 7B model:
- Zero API costs (runs locally)
- 120-second timeout for generation
- Token estimation based on character count
- Full request/response audit trail in `recipe_generations` table

#### Security Model

1. **Password Hashing**: BCrypt with cost factor 12
2. **JWT Tokens**:
   - Access token: 15-minute expiration, contains userId and email
   - Refresh token: 7-day expiration, used to obtain new access tokens
   - Signing algorithm: HS512 with base64-encoded secret
3. **Token Storage**: Client-side; server validates signature and expiration
4. **Protected Endpoints**: Spring Security filter chain validates JWT on each request

#### Database Schema

```sql
-- User accounts
users (
  id UUID PRIMARY KEY,
  email VARCHAR UNIQUE NOT NULL,
  password_hash VARCHAR NOT NULL,
  name VARCHAR,
  created_at TIMESTAMP DEFAULT NOW()
)

-- Generated recipes
recipes (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id),
  title VARCHAR NOT NULL,
  description TEXT,
  prep_time INT,
  cook_time INT,
  total_time INT,
  servings INT,
  difficulty VARCHAR,
  cuisine VARCHAR,
  ingredients_used TEXT[],
  instructions TEXT,  -- JSON string
  ingredients TEXT,   -- JSON string
  nutrition_info TEXT, -- JSON string
  tags TEXT[],
  is_saved BOOLEAN DEFAULT false,
  created_at TIMESTAMP DEFAULT NOW()
)

-- LLM generation audit log
recipe_generations (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id),
  ingredients TEXT[],
  dietary_preferences TEXT[],
  prompt TEXT,
  raw_response TEXT,
  model_used VARCHAR,
  tokens_used INT,
  cost_cents INT,
  cached BOOLEAN DEFAULT false,
  latency_ms BIGINT,
  status VARCHAR,  -- SUCCESS, FAILED
  error_message TEXT,
  created_at TIMESTAMP DEFAULT NOW()
)

-- Prepared for future caching implementation
llm_cache (
  id UUID PRIMARY KEY,
  input_hash VARCHAR(64) UNIQUE,
  response TEXT,
  model VARCHAR,
  tokens_used INT,
  hit_count INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT NOW(),
  expires_at TIMESTAMP
)
```

### Planned Enhancements

#### Smart Caching Layer
- **Input Hashing**: SHA-256 of normalized prompt (ingredients sorted, lowercased)
- **TTL**: Configurable expiration (e.g., 7 days)
- **Storage**: `llm_cache` table
- **Hit Rate Tracking**: Monitor cache effectiveness

#### Resilience Patterns
- **Circuit Breaker**: Fail-fast when Ollama is unavailable
- **Retry Logic**: Exponential backoff for transient failures
- **Rate Limiting**: Per-user request throttling
- **Bulkhead**: Isolate LLM thread pool

#### AWS Integration
- **S3**: Recipe exports and user-generated content
- **CloudWatch**: Centralized logging and metrics
- **Secrets Manager**: Secure credential storage
- **ECS Fargate**: Containerized deployment

---

## 🔧 Development

### Running the Application

```bash
# Start dependencies
docker-compose up -d

# Run migrations
./mvnw flyway:migrate

# Start Spring Boot
./mvnw spring-boot:run
```

### Testing

```bash
# Run end-to-end test script
./test-recipe-generation.sh

# Expected output:
# ✓ Authentication: Working
# ✓ JWT Protection: Working
# ✓ AI Recipe Generation: Working
# ✓ Database Persistence: Working
```

### Database Migrations

Flyway manages schema changes via SQL scripts in `src/main/resources/db/migration/`.

```bash
# Apply pending migrations
./mvnw flyway:migrate

# View migration history
./mvnw flyway:info

# Clean database (WARNING: Drops all tables)
./mvnw flyway:clean
```

### Environment Variables

| Variable                | Description                        | Default                          |
|-------------------------|------------------------------------|---------------------------------|
| `DATABASE_URL`          | PostgreSQL connection string       | `localhost:5432/masterchef`     |
| `DATABASE_USERNAME`     | Database user                      | `dev`                           |
| `DATABASE_PASSWORD`     | Database password                  | `dev`                           |
| `JWT_SECRET`            | Secret key for signing JWTs        | (base64-encoded random string)  |
| `JWT_ACCESS_EXPIRATION` | Access token expiration (ms)       | `900000` (15 minutes)           |
| `JWT_REFRESH_EXPIRATION`| Refresh token expiration (ms)      | `604800000` (7 days)            |
| `OLLAMA_BASE_URL`       | Ollama API endpoint                | `http://localhost:11434`        |
| `OLLAMA_MODEL`          | Ollama model name                  | `mistral`                       |

These are configured in `application.yml`.

---

## 📚 Documentation

- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** - Detailed system design and technical decisions
- **[SCOPE.md](docs/SCOPE.md)** - Feature requirements and project scope
- **[test-recipe-generation.sh](test-recipe-generation.sh)** - End-to-end test script with examples

---

## 🤝 Contributing

This is a personal learning project showcasing production-ready engineering practices. Feedback and suggestions are welcome!

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](../LICENSE) file for details.

---

**Built with ❤️ using Java 21, Spring Boot, and PostgreSQL**

*Designed for production. Runs locally for free.*
