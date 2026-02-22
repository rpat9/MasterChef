# MasterChef

> AI-powered recipe generation platform with production-grade backend infrastructure

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)
[![Terraform](https://img.shields.io/badge/Terraform-1.5+-purple.svg)](https://www.terraform.io/)

---

## 📋 Overview

MasterChef is a full-stack application that generates AI-powered recipes using local LLM inference with JWT authentication, PostgreSQL persistence, and AWS-native infrastructure.

### Key Features

- 🔐 **JWT Authentication** - Secure user registration and login with Spring Security
- 🤖 **Local AI Inference** - Mistral 7B via Ollama (zero API costs)
- 📊 **Full Observability** - Metrics, logging, caching, and audit trails
- ☁️ **Cloud-Ready** - Production Terraform infrastructure for AWS deployment
- 🐳 **Docker Compose** - Complete local development environment

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (React)                      │
│  TypeScript • Vite • TailwindCSS • shadcn/ui                │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP/REST
┌──────────────────────────▼──────────────────────────────────┐
│                   Backend (Spring Boot)                      │
│  Java 21 • JWT Auth • LLM Orchestrator • Resilience4j       │
│  S3 Storage • CloudWatch • Secrets Manager                  │
└──────┬───────────────────┬───────────────────┬──────────────┘
       │                   │                   │
       ▼                   ▼                   ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ PostgreSQL   │  │ Ollama       │  │ LocalStack   │
│ (Database)   │  │ (Mistral 7B) │  │ (AWS Mock)   │
└──────────────┘  └──────────────┘  └──────────────┘
```

---

## 📁 Project Structure

```
MasterChef/
├── frontend/                    # React TypeScript frontend
├── masterchef-backend/          # Spring Boot REST API
│   ├── src/main/java/.../
│   │   ├── config/             # AWS, Security, Health checks
│   │   ├── controller/         # REST endpoints
│   │   ├── service/            # Business logic
│   │   ├── repository/         # JPA repositories
│   │   ├── models/             # JPA entities
│   │   ├── dto/                # Request/Response objects
│   │   └── exception/          # Global exception handling
│   └── src/main/resources/
│       ├── application.yml     # Application config
│       └── db/migration/       # Flyway SQL migrations
├── infrastructure/              # Production Terraform modules
│   └── terraform/
│       ├── modules/            # Reusable infrastructure components
│       │   ├── networking/    # VPC, subnets, NAT gateways
│       │   ├── iam/           # Task execution/task roles
│       │   ├── rds/           # PostgreSQL with backups
│       │   └── ecs/           # Fargate, ALB, auto-scaling
│       └── environments/
│           └── dev/           # Dev environment configuration
├── docker-compose.yml          # Local development stack
└── init-localstack.sh         # LocalStack initialization
```

---

## 🚀 Quick Start

### Prerequisites

- Java 21
- Node.js 18+
- Docker & Docker Compose
- Maven (or use `./mvnw`)

### 1. Start Infrastructure

```bash
# Start PostgreSQL, Ollama, and LocalStack
docker-compose up -d

# Wait for Ollama to pull model (~4.4GB)
docker logs -f ollama
```

### 2. Run Backend

```bash
cd masterchef-backend
./mvnw spring-boot:run
```

**Backend running at:** http://localhost:8080

**Health check:** http://localhost:8080/actuator/health

### 3. Run Frontend

```bash
cd frontend
npm install
npm run dev
```

**Frontend running at:** http://localhost:5173

---

## 🔑 Key Technologies

### Backend
- **Java 21** - Modern Java with virtual threads and records
- **Spring Boot 4.0.2** - Enterprise application framework
- **PostgreSQL 16** - Primary database with Flyway migrations
- **JWT + Spring Security** - Stateless authentication
- **Ollama (Mistral 7B)** - Local LLM inference
- **AWS SDK v2** - S3, CloudWatch, Secrets Manager integration
- **Resilience4j** - Circuit breaker, retry, rate limiting
- **LocalStack** - AWS service emulation for local dev

### Frontend
- **React 18** - Modern UI library
- **TypeScript** - Type-safe JavaScript
- **Vite** - Fast build tooling
- **TailwindCSS** - Utility-first styling
- **shadcn/ui** - Accessible component library

### Infrastructure
- **Terraform** - Infrastructure as Code for AWS
- **ECS Fargate** - Serverless container orchestration
- **RDS PostgreSQL** - Managed database with Multi-AZ
- **Application Load Balancer** - HTTPS termination and routing
- **Auto Scaling** - CPU/Memory-based scaling policies
- **CloudWatch** - Centralized logging and monitoring

---

## ☁️ AWS Deployment

The project includes production-ready Terraform infrastructure. See [`infrastructure/README.md`](infrastructure/README.md) for detailed deployment instructions.

**Quick Preview:**

```bash
cd infrastructure/terraform/environments/dev
terraform init
terraform plan  # Shows what would be deployed (no actual changes)
```

---

## 📚 Documentation

- **Backend API:** [`masterchef-backend/README.md`](masterchef-backend/README.md)
- **Infrastructure:** [`infrastructure/README.md`](infrastructure/README.md)
- **Architecture:** [`masterchef-backend/docs/ARCHITECTURE.md`](masterchef-backend/docs/ARCHITECTURE.md)
- **Project Scope:** [`masterchef-backend/docs/SCOPE.md`](masterchef-backend/docs/SCOPE.md)

---

## 🎯 Design Philosophy

### Architecture Principles

1. **Local Development First** - Complete feature parity with Docker Compose
2. **Cloud-Ready Architecture** - Terraform infrastructure targeting AWS ECS, RDS, and S3
3. **Enterprise Patterns** - Security, observability, resilience built-in
4. **Cost-Aware LLM** - Local inference with Ollama eliminates per-request API costs

---

## 🔐 Security

- **Password Hashing:** BCrypt with cost factor 12
- **JWT Tokens:** 15-minute access tokens, 7-day refresh tokens
- **Secrets Management:** AWS Secrets Manager (LocalStack locally)
- **Database Credentials:** Stored in Secrets Manager, not in code
- **IAM Roles:** Least-privilege policies for ECS tasks
- **Network Security:** Private subnets, security groups, no public DB access

---

## 📊 Monitoring & Observability

- **Health Checks:** Spring Boot Actuator with custom indicators
- **Metrics:** Micrometer with cache hit rates, LLM latency
- **Logging:** Structured logging with SLF4J + Logback
- **AWS CloudWatch:** Centralized logs and custom metrics
- **Alarms:** CPU, memory, response time monitoring

---

## 🤝 Contributing

Feedback and suggestions are welcome.

---

## 📄 License

[MIT License](LICENSE)

---

## 👤 Author

For questions or collaboration: [Create an issue](https://github.com/rpat9/MasterChef/issues)
