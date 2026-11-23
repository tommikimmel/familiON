# FamiliON Core API (`backend/`)

Spring Boot 3.5.x Core API built with Java 21, Maven Wrapper, Flyway, PostgreSQL 17, Redis, and Testcontainers.

## Requirements

- Java 21 (JDK 21)
- Docker (required for Testcontainers integration tests and container builds)
- PostgreSQL 17 and Redis 7 when running locally outside tests

## Run locally

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Default port: `8081`

- Health endpoint: `http://localhost:8081/actuator/health`
- OpenAPI spec: `http://localhost:8081/v3/api-docs`
- Swagger UI: `http://localhost:8081/swagger-ui.html`

## Run tests

Unit tests only:

```bash
./mvnw -B test
```

Full verification including Testcontainers integration tests (requires Docker):

```bash
./mvnw -B verify
```

## Build Docker image

From the repository root:

```bash
docker build -t familion-backend ./backend
```
