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

The system follows a microservices architecture, with each service owning its own domain and communicating asynchronously over Kafka where appropriate.

| Service | Description | Status |
|---|---|---|
| `api-gateway` | Entry point routing requests to downstream services | Planned |
| `accounting-service` | Manages accounts and ledger records | ✅ Active |
| `transaction-service` | Handles transaction processing | ✅ Active |
| `payment-service` | Handles payment operations | Planned |
| `fraud-detection-service` | Monitors transactions for fraud, backed by Redis | Planned |
| `notification-service` | Sends user notifications (e.g. transaction alerts) | Planned |

> `accounting-service` and `transaction-service` are the two services currently wired up end-to-end (Postgres + Kafka). Update this table as the others come online.

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 4.1.1
- **Database:** PostgreSQL 18
- **Caching / Rate Limiting:** Redis
- **Messaging:** Apache Kafka 7.5.3 (with Zookeeper)
- **Containerization:** Docker & Docker Compose

## Prerequisites

- Java 17 (`openjdk-17-jdk`)
- Docker & Docker Compose
- Maven (or use the included `mvnw` wrapper)

## Getting Started

### 1. Clone the repository

```bash
git clone <repo-url>
cd banking-system
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

### 3. Run a service

From within a service directory (e.g. `accounting-service`):

```bash
./mvnw spring-boot:run
```

Or build and run the jar directly:

```bash
./mvnw clean package
java -jar target/*.jar
```

### 4. Verify it's running

```bash
curl http://localhost:9001/actuator/health
```

## Configuration

Each service reads its configuration from `application.yaml`. Services running on the **host machine** (e.g. from an IDE) connect to infrastructure via `localhost` on the host-mapped ports below — not the internal Docker ports.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/bank_account_db
    username: banking_user
    password: banking_password
  kafka:
    bootstrap-servers: localhost:9092
```

> **Note:** Default credentials are for local development only. Do not commit real credentials — use environment variables or a secrets manager for any non-local environment.

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
| Kafka (internal, `PLAINTEXT`) | 29092 | — |
| Kafka (host, `PLAINTEXT_HOST`) | 9092 | 9092 |
| Zookeeper | 2181 | 2181 |
| `accounting-service` | 9001 | 9001 |
| `transaction-service` | _9002_ | _9002_ |
| `payment-service` | _9008_ | _9008_ |
| `notification-service` | _9009_ | _9009_ |
| `fraud-detection-service` | _9003_ | _9003_ |
| `api-gateway` | _8000_ | _8000_ |

*(Update this table as other services are assigned ports.)*

## Health Checks

Each Spring Boot service exposes actuator health and info endpoints:

```
GET /actuator/health
GET /actuator/info
```

## Project Structure

```
banking-system/
├── api-gateway/
├── accounting-service/
├── transaction-service/
├── payment-service/
├── fraud-detection-service/
├── notification-service/
├── postgres-init/
│   └── init-multiple-dbs.sh
└── docker-compose.yml
```

## Troubleshooting

- **`environment variable "KAFKA_PROCESS_ROLES" is not set"`** — the `confluentinc/cp-kafka:latest` tag now defaults to Kafka 4.x, which dropped Zookeeper support (KRaft-only). Pin an older tag that still supports Zookeeper mode, e.g. `confluentinc/cp-kafka:7.5.3`.
- **`Each listener must have a different port`** — `PLAINTEXT` (internal) and `PLAINTEXT_HOST` (host) listeners were both defaulting to 9092. Set `KAFKA_LISTENERS` explicitly with separate ports (internal `29092`, host `9092`).
- **Postgres `password authentication failed` / target database doesn't exist** — usually means `postgres-init/` scripts never ran. They only execute once, against a completely empty data volume. Reset with `docker compose down -v && docker compose up -d`, then confirm with `docker exec -it postgres psql -U banking_user -d postgres -c '\l'`.
- **`NoClassDefFoundError: com/fasterxml/jackson/databind/JavaType`** — see [Kafka JSON serialization dependency](#configuration) above.

## Contributing

Contributions are welcome. Please open an issue to discuss significant changes before submitting a pull request.

## License

_TBD_