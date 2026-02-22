# MasterChef Infrastructure

> Production-grade AWS infrastructure defined in Terraform  
> **Note:** Infrastructure architecture designed with assistance from Claude (Anthropic)

[![Terraform](https://img.shields.io/badge/Terraform-1.5+-purple.svg)](https://www.terraform.io/)
[![AWS](https://img.shields.io/badge/AWS-Ready-orange.svg)](https://aws.amazon.com/)

---

## 📋 Overview

This directory contains production-ready Terraform modules for deploying MasterChef backend to AWS. The infrastructure uses multi-AZ deployment, auto-scaling, security best practices, and comprehensive monitoring.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Internet                              │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Application Load Balancer (ALB)                 │
│  • HTTPS termination • Health checks • Path-based routing    │
└──────────────────┬──────────────────────────────────────────┘
                   │ Port 8080
                   ▼
┌─────────────────────────────────────────────────────────────┐
│                ECS Fargate Cluster                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Task 1      │  │  Task 2      │  │  Task 3      │      │
│  │  (Backend)   │  │  (Backend)   │  │  (Backend)   │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │ Auto-scaling (1-4 tasks)          │               │
└─────────┼────────────────────────────────────┼──────────────┘
          │                                    │
          ▼                                    ▼
┌──────────────────────┐           ┌──────────────────────┐
│  RDS PostgreSQL      │           │  S3 Bucket           │
│  • Multi-AZ          │           │  • Recipe exports    │
│  • Automated backups │           │  • Versioning        │
│  • Encryption        │           │  • Server-side enc   │
└──────────────────────┘           └──────────────────────┘
          │
          ▼
┌──────────────────────┐
│  Secrets Manager     │
│  • DB credentials    │
│  • API keys          │
└──────────────────────┘
```

### Network Architecture

```
VPC (10.0.0.0/16)
│
├── Public Subnets (us-east-1a, us-east-1b)
│   ├── 10.0.0.0/20
│   ├── 10.0.16.0/20
│   ├── Application Load Balancer
│   └── NAT Gateways
│
└── Private Subnets (us-east-1a, us-east-1b)
    ├── 10.0.32.0/20
    ├── 10.0.48.0/20
    ├── ECS Tasks (Fargate)
    └── RDS Instances
```

---

## 📁 Directory Structure

```
infrastructure/
└── terraform/
    ├── modules/
    │   ├── networking/          # VPC, subnets, NAT, route tables
    │   │   ├── main.tf
    │   │   ├── variables.tf
    │   │   └── outputs.tf
    │   ├── iam/                 # Task execution & task roles
    │   │   ├── main.tf
    │   │   ├── variables.tf
    │   │   └── outputs.tf
    │   ├── rds/                 # PostgreSQL with backups
    │   │   ├── main.tf
    │   │   ├── variables.tf
    │   │   └── outputs.tf
    │   └── ecs/                 # Fargate, ALB, auto-scaling
    │       ├── main.tf
    │       ├── variables.tf
    │       └── outputs.tf
    └── environments/
        └── dev/                 # Development environment
            ├── main.tf          # Module orchestration
            ├── variables.tf     # Variable definitions
            ├── outputs.tf       # Output values
            ├── terraform.tfvars # Environment configuration
            └── README.md        # Deployment guide
```

---

## 🚀 Quick Start

### Prerequisites

- Terraform >= 1.5.0
- AWS CLI configured with credentials
- Docker (for building/pushing images)

### 1. Review Infrastructure

```bash
cd terraform/environments/dev

# Initialize Terraform
terraform init

# Preview what would be created
terraform plan
```

**This shows all resources WITHOUT creating them.** Perfect for:
- Architecture reviews
- Validating module configuration
- Pre-deployment verification

### 2. Deploy to AWS

```bash
# Review plan
terraform plan -out=plan.tfplan

# Deploy
terraform apply plan.tfplan
```

**Estimated Monthly Cost:**
- ECS Fargate (0.5 vCPU, 1GB): ~$15/month
- RDS db.t3.micro: ~$15/month  
- Application Load Balancer: ~$20/month
- NAT Gateway: ~$35/month
- Data transfer: ~$5/month
- **Total: ~$90/month (dev environment)**

### 3. Access Application

```bash
# Get application URL
terraform output application_url

# Test health endpoint
curl $(terraform output -raw application_url)/actuator/health
```

### 4. Cleanup

```bash
# Destroy all resources
terraform destroy

# Confirm when prompted
yes
```

---

## 📦 Terraform Modules

### 1. Networking Module (`modules/networking/`)

Creates the VPC foundation for all resources.

**Resources:**
- VPC with DNS support enabled
- Public subnets (2 AZs) for ALB and NAT gateways
- Private subnets (2 AZs) for ECS tasks and RDS
- Internet Gateway for public subnet internet access
- NAT Gateways (one per AZ) for private subnet egress
- Route tables with appropriate routes
- VPC Flow Logs (optional) for network monitoring

**Outputs:**
- `vpc_id` - VPC identifier
- `public_subnet_ids` - List of public subnet IDs
- `private_subnet_ids` - List of private subnet IDs

### 2. IAM Module (`modules/iam/`)

Creates least-privilege IAM roles for ECS tasks.

**Resources:**
- **Task Execution Role** - Used by ECS to pull images, write logs, fetch secrets
  - ECR image pull permissions
  - CloudWatch Logs write permissions
  - Secrets Manager read permissions
- **Task Role** - Used by application code running in containers
  - S3 bucket access (recipe exports)
  - CloudWatch Logs write permissions
  - Secrets Manager read permissions
  - CloudWatch Metrics write permissions
  - RDS IAM authentication (optional)

**Security Principle:** Task Execution Role ≠ Task Role
- Execution role: AWS infrastructure needs
- Task role: Application code needs

**Outputs:**
- `ecs_task_execution_role_arn` - ARN of execution role
- `ecs_task_role_arn` - ARN of task role

### 3. RDS Module (`modules/rds/`)

Provisions managed PostgreSQL database with production features.

**Resources:**
- RDS PostgreSQL instance (configurable version)
- DB subnet group across multiple AZs
- Security group (PostgreSQL ingress from ECS only)
- Secrets Manager secret for credentials
- Enhanced monitoring IAM role
- CloudWatch alarms (CPU, storage)

**Features:**
- Multi-AZ deployment (optional, disabled by default in dev)
- Automated backups (7-day retention)
- Encryption at rest (always enabled)
- Performance Insights (optional)
- Deletion protection (configurable)
- PostgreSQL CloudWatch log exports

**Outputs:**
- `db_instance_endpoint` - Database connection endpoint
- `db_secret_arn` - Secrets Manager ARN for credentials
- `db_security_group_id` - Security group ID

### 4. ECS Module (`modules/ecs/`)

Deploys containerized application with auto-scaling and load balancing.

**Resources:**
- **ECS Cluster** with Container Insights
- **ECS Task Definition** with:
  - Container configuration
  - Environment variables
  - Secrets (from Secrets Manager)
  - Log configuration (CloudWatch)
  - Health check
- **ECS Service** with:
  - Fargate launch type
  - Rolling deployment (100%-200% capacity)
  - Circuit breaker (auto-rollback on failure)
  - Load balancer integration
- **Application Load Balancer**
  - HTTP listener (can redirect to HTTPS)
  - Target group with health checks
  - Cross-zone load balancing
- **Auto Scaling**
  - Target tracking (CPU 70%, Memory 80%)
  - Min/max capacity limits
  - Scale-in/out cooldown periods
- **S3 Bucket** for recipe exports
  - Versioning enabled
  - Server-side encryption
  - Public access blocked
- **CloudWatch Alarms**
  - ECS CPU utilization
  - ALB response time

**Outputs:**
- `alb_dns_name` - Load balancer DNS name
- `cluster_name` - ECS cluster name
- `service_name` - ECS service name
- `s3_bucket_name` - Recipe export bucket

---

## 🎯 Key Infrastructure Features

This infrastructure includes:

✅ **Multi-AZ High Availability** - Resources deployed across 2+ availability zones  
✅ **Auto-Scaling** - ECS tasks scale based on CPU/memory metrics  
✅ **Security Best Practices** - Private subnets, security groups, least-privilege IAM  
✅ **Secrets Management** - Database credentials in AWS Secrets Manager  
✅ **Encryption** - RDS encryption, S3 server-side encryption  
✅ **Monitoring** - CloudWatch logs, metrics, and alarms  
✅ **Infrastructure as Code** - Modular, reusable Terraform patterns  
✅ **Cost Optimization** - Configurable resources (disable Multi-AZ, NAT for dev)  
✅ **Deployment Safety** - Circuit breakers, health checks, rolling updates  

---

## 🔧 Configuration

### Environment Variables

Key variables in `terraform.tfvars`:

```hcl
# Project
project_name = "masterchef"
environment  = "dev"
aws_region   = "us-east-1"

# ECS Configuration
task_cpu      = "512"   # 0.5 vCPU
task_memory   = "1024"  # 1GB RAM
desired_count = 1       # Number of tasks

# RDS Configuration
db_instance_class    = "db.t3.micro"
enable_multi_az      = false
enable_deletion_protection = false

# Cost Optimization
enable_nat_gateway = true  # Set false to save ~$35/month
```

### Cost Optimization Tips

**Development:**
```hcl
enable_multi_az             = false  # Single AZ
enable_nat_gateway          = false  # No NAT ($35/mo savings)
enable_deletion_protection  = false  # Easy teardown
db_instance_class           = "db.t3.micro"
desired_count               = 1
```

**Production:**
```hcl
enable_multi_az             = true   # High availability
enable_nat_gateway          = true   # Private subnet egress
enable_deletion_protection  = true   # Prevent accidental deletion
db_instance_class           = "db.t3.small"  # More capacity
desired_count               = 2      # Multiple tasks
```

---

## 🔐 Security

### Network Security

- **Private Subnets** - ECS tasks and RDS in private subnets (no direct internet access)
- **Security Groups** - Strict ingress/egress rules:
  - ALB: HTTP/HTTPS from internet
  - ECS: Port 8080 from ALB only
  - RDS: Port 5432 from ECS only
- **NAT Gateways** - Outbound internet for private subnets (pulling images, accessing AWS APIs)

### IAM Security

- **Least Privilege** - Each role has minimum required permissions
- **No Hardcoded Credentials** - All secrets in Secrets Manager
- **Task Roles** - Application uses IAM roles, not API keys
- **Separate Roles** - Execution role ≠ Task role (separation of concerns)

### Data Security

- **Encryption at Rest** - RDS and S3 encrypted
- **Secrets Management** - Database passwords in Secrets Manager
- **HTTPS** - ALB can terminate SSL/TLS (certificate required)
- **VPC Isolation** - Resources cannot communicate across VPCs

---

## 📊 Monitoring

### CloudWatch Logs

- `/ecs/masterchef-backend` - Application logs from ECS tasks
- `/aws/rds/instance/masterchef-postgres/postgresql` - PostgreSQL logs
- VPC Flow Logs (optional) - Network traffic monitoring

### CloudWatch Alarms

- **ECS CPU High** - Alerts when CPU > 85%
- **RDS CPU High** - Alerts when CPU > 80%
- **RDS Storage Low** - Alerts when < 5GB free
- **ALB Response Time** - Alerts when > 1 second

### Metrics

- ECS task count, CPU, memory utilization
- ALB request count, response time, HTTP errors
- RDS CPU, memory, storage, connections
- Custom application metrics (via CloudWatch SDK)

---

## 🚀 Deployment Workflow

### Initial Deployment

```bash
# 1. Initialize Terraform
cd terraform/environments/dev
terraform init

# 2. Review plan
terraform plan

# 3. Apply (if deploying)
terraform apply

# 4. Build and push Docker image
docker build -t <account>.dkr.ecr.us-east-1.amazonaws.com/masterchef-backend:latest ../../masterchef-backend
docker push <account>.dkr.ecr.us-east-1.amazonaws.com/masterchef-backend:latest

# 5. Update ECS service
aws ecs update-service --cluster masterchef-cluster --service masterchef-backend-service --force-new-deployment
```

### Updates

```bash
# Code changes
docker build -t <image> .
docker push <image>
aws ecs update-service --cluster <cluster> --service <service> --force-new-deployment

# Infrastructure changes
terraform plan
terraform apply
```

### Rollback

```bash
# Automatic: Circuit breaker rolls back failed deployments

# Manual: Deploy previous task definition
aws ecs update-service --cluster <cluster> --service <service> --task-definition <previous-arn>
```

---

## 📚 Reference

### Terraform Concepts Used

- **Modules** - Reusable infrastructure components
- **Variables** - Parameterized configurations
- **Outputs** - Exporting values between modules
- **Data Sources** - Querying existing AWS resources
- **Dependencies** - Resource creation ordering
- **Count** - Creating multiple similar resources
- **Conditional Resources** - Using `count` for optional resources

### AWS Services Used

- **VPC** - Virtual Private Cloud networking
- **ECS** - Elastic Container Service (serverless Fargate)
- **RDS** - Relational Database Service (PostgreSQL)
- **ALB** - Application Load Balancer
- **S3** - Simple Storage Service
- **Secrets Manager** - Secure credential storage
- **CloudWatch** - Logging and monitoring
- **IAM** - Identity and Access Management

---

## 🤝 Acknowledgments

Infrastructure architecture designed with assistance from Claude (Anthropic).

---

## 📄 License

[MIT License](../LICENSE)

---

## 📚 Additional Resources

- [Terraform AWS Provider Docs](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [AWS ECS Best Practices](https://docs.aws.amazon.com/AmazonECS/latest/bestpracticesguide/)
- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)
- [Terraform Module Best Practices](https://www.terraform.io/docs/modules/index.html)
