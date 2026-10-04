# Banking System

A microservices-based banking platform built with **Spring Boot**, **PostgreSQL**, **Kafka**, and **Redis** — covering account management, transaction processing, payments, and real-time fraud detection.

## Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Ports](#ports)
- [Health Checks](#health-checks)
- [Project Structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Architecture

The system follows a microservices architecture, with each service owning its own domain and communicating asynchronously over Kafka where appropriate. Service discovery is handled by Eureka, and shared configuration is centralized in a Config Server.

| Service | Description | Status |
|---|---|---|
| `eureka-server` | Service registry / discovery | ✅ Active |
| `config-service` | Centralized configuration server | ✅ Active |
| `api-gateway` | Entry point routing requests to downstream services | Planned |
| `account-service` | Manages accounts and ledger records | ✅ Active |
| `transaction-service` | Handles transaction processing | ✅ Active |
| `payment-service` | Handles payment operations | active |
| `fraud-detection-service` | Monitors transactions for fraud, backed by Redis | active |
| `notification-service` | Sends user notifications (e.g. transaction alerts) | active |

> `account-service` and `transaction-service` are the two services currently wired up end-to-end (Postgres + Kafka). Update this table as the others come online.

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 4.1.1
- **Database:** PostgreSQL 18
- **Caching / Rate Limiting:** Redis
- **Messaging:** Apache Kafka 7.5.3 (with Zookeeper)
- **Service Discovery:** Eureka
- **Configuration:** Spring Cloud Config Server
- **Containerization:** Docker & Docker Compose

## Prerequisites

- Java 17 (`openjdk-17-jdk`)
- Docker & Docker Compose
- Maven (or use the included `mvnw` wrapper)

## Getting Started

### 1. Clone the repository

```bash
git clone <repo-url>
cd banking-system-ms/cloud
```

### 2. Start infrastructure services

This spins up PostgreSQL, Redis, Kafka, and Zookeeper:

```bash
docker compose up -d
```

On first boot, Postgres runs the scripts in `postgres-init/`, creating `bank_account_db` and `transaction_db`. Confirm they exist:

```bash
docker exec -it postgres psql -U banking_user -d postgres -c '\l'
```

> If you ever need to reset the databases (e.g. after editing `postgres-init/`), the init scripts only run against an **empty** data volume:
> ```bash
> docker compose down -v
> docker compose up -d
> ```

### 3. Start core platform services

`eureka-server` and `config-service` must be running **before** any other service starts, since every service fetches its configuration from Config Server at boot and registers with Eureka.

```bash
# from cloud/eureka-server
./mvnw spring-boot:run

# from cloud/config-service
./mvnw spring-boot:run
```

### 4. Run a business service

From within a service directory (e.g. `cloud/services/account-service`):

```bash
./mvnw spring-boot:run
```

Or build and run the jar directly:

```bash
./mvnw clean package
java -jar target/*.jar
```

### 5. Verify it's running

```bash
curl http://localhost:10001/actuator/health
```

## Configuration

Shared defaults live in `config-service/config/application.yml`, with per-service overrides in `config-service/config/<service-name>.yml`. Each service fetches its configuration from Config Server at startup rather than reading a local `application.yaml` directly.

Services running on the **host machine** (e.g. from an IDE) connect to infrastructure via `localhost` on the host-mapped ports below — not the internal Docker ports.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/bank_account_db
    username: banking_user
    password: banking_password
  kafka:
    bootstrap-servers: localhost:9092
```

> **Note:** Default credentials are for local development only. Do not commit real credentials — use environment variables or a secrets manager for any non-local environment. A `.env` file at `cloud/.env` is used for local secrets and should not be committed.

> **Note:** Once services are containerized and run *inside* `banking-network`, switch these to the internal hostnames/ports instead — `postgres:5432` and `kafka:29092`. A dedicated Spring profile (e.g. `docker`) is a good way to manage this split.

### Kafka JSON serialization dependency

Spring Boot 4 ships with Jackson 3 by default, but Spring Kafka's `JsonSerializer`/`JsonDeserializer` still depend on classic Jackson 2 (`com.fasterxml.jackson.databind`), which isn't pulled in automatically. Any service using Kafka JSON (de)serialization needs this added explicitly to its `pom.xml`:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

## Ports

| Service | Container Port | Host Port |
|---|---|---|
| PostgreSQL | 5432 | 5433 |
| Redis | 6379 | 6379 |
| Eureka | 8761 | 8761 |
| Config Server | _9999_ | _9999_ |
| Kafka (internal, `PLAINTEXT`) | 29092 | — |
| Kafka (host, `PLAINTEXT_HOST`) | 9092 | 9092 |
| Zookeeper | 2181 | 2181 |
| `account-service` | 10001 | 10001 |
| `transaction-service` | _9002 or 10006?_ | _9002 or 10006?_ |
| `payment-service` | _10005_ | _10005_ |
| `notification-service` | _10003_ | _10003_ |
| `fraud-detection-service` | _10002_ | _10002_ |
| `api-gateway` | _7000_ | _7000_ |

*(Update this table as other services are assigned ports.)*

> ⚠️ **transaction-service port is unconfirmed** — the config/README has said `9002`, but a local run was observed on Tomcat port `10006`. Check `config-service/config/transaction-service.yml` (or the service's `server.port`) and correct this row.

## Health Checks

Each Spring Boot service exposes actuator health and info endpoints:

```
GET /actuator/health
GET /actuator/info
```

## Project Structure

```
banking-system-ms/
└── cloud/
    ├── api-gateway/
    ├── config-service/
    │   ├── .mvn/
    │   ├── config/
    │   │   ├── account-service.yml
    │   │   ├── api-gateway.yml
    │   │   ├── application.yml
    │   │   ├── eureka-server.yml
    │   │   ├── fraud-detection-service.yml
    │   │   ├── notification-service.yml
    │   │   ├── payment-service.yml
    │   │   └── transaction-service.yml
    │   └── pom.xml
    ├── eureka-server/
    ├── pom.xml
    ├── postgres-init/
    │   └── init-multiple-dbs.sh
    ├── services/
    │   ├── account-service/
    │   ├── fraud-detection-service/
    │   ├── notification-service/
    │   ├── payment-service/
    │   └── transaction-service/
    ├── .env
    └── docker-compose.yml
```

## Troubleshooting

- **`environment variable "KAFKA_PROCESS_ROLES" is not set"`** — the `confluentinc/cp-kafka:latest` tag now defaults to Kafka 4.x, which dropped Zookeeper support (KRaft-only). Pin an older tag that still supports Zookeeper mode, e.g. `confluentinc/cp-kafka:7.5.3`.
- **`Each listener must have a different port`** — `PLAINTEXT` (internal) and `PLAINTEXT_HOST` (host) listeners were both defaulting to 9092. Set `KAFKA_LISTENERS` explicitly with separate ports (internal `29092`, host `9092`).
- **Postgres `password authentication failed` / target database doesn't exist** — usually means `postgres-init/` scripts never ran. They only execute once, against a completely empty data volume. Reset with `docker compose down -v && docker compose up -d`, then confirm with `docker exec -it postgres psql -U banking_user -d postgres -c '\l'`.
- **Postgres `password authentication failed for user "${POSTGRES_USER}"`** (literal placeholder in the error) — the environment variable wasn't resolved before reaching Postgres. Check that `POSTGRES_USER` / `POSTGRES_PASSWORD` are set wherever the value originates — typically in `config-service`'s environment or the IDE run configuration for the consuming service — and confirm what Config Server actually serves via `http://localhost:9999/<service-name>/default`.
- **`NoClassDefFoundError: com/fasterxml/jackson/databind/JavaType`** — see [Kafka JSON serialization dependency](#configuration) above.

## Contributing

Contributions are welcome. Please open an issue to discuss significant changes before submitting a pull request.

## License

_TBD_