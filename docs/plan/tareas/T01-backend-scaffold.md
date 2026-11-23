# T01 — feat(backend): scaffold Core API (Spring Boot + Flyway + Testcontainers)

| Fase | Ola | Rama | Requiere mergeado | Paralela con | Migración reservada |
|---|---|---|---|---|---|
| 0 | 0.1 | `feat/backend-scaffold` | — | T02, T03 | `V1` |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` (completo: se copia tal cual como `V1`).

## Objetivo
Crear el proyecto `backend/` (Core API) compilable, testeado contra PostgreSQL 17 real, con la migración inicial aplicada y listo para contenerizar. Sin lógica de negocio ni seguridad (eso llega en T06).

## Archivos permitidos
```
backend/**
```

## Especificación

1. **Generar el proyecto** (Spring Initializr o equivalente) en `backend/`:
   - Maven, Java 21, Spring Boot **3.5.x** (última patch estable), `groupId=com.familion`, `artifactId=familion-backend`, `packageName=com.familion`, clase `com.familion.FamilionApplication`.
   - Dependencias: `spring-boot-starter-web`, `-validation`, `-data-jpa`, `-actuator`, `-data-redis`, `-mail`, `-thymeleaf`, `postgresql` (runtime), `flyway-core` + `flyway-database-postgresql`, `springdoc-openapi-starter-webmvc-ui` (2.8.x), test: `spring-boot-starter-test`, `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`.
   - **NO** agregar `spring-boot-starter-security` ni `oauth2-resource-server` (los agrega T06).
   - Maven Wrapper: `mvn -N wrapper:wrapper -Dmaven=3.9.9` (commitear `mvnw`, `mvnw.cmd`, `.mvn/`).
2. **Migración:** `src/main/resources/db/migration/V1__init_schema.sql` = copia **byte a byte** de `docs/database/schema.sql`.
3. **Configuración** `src/main/resources/application.yml`:
   ```yaml
   server.port: 8081
   spring:
     application.name: familion-backend
     datasource: { url: ${DB_URL:jdbc:postgresql://localhost:5432/familion}, username: ${DB_USER:familion}, password: ${DB_PASSWORD:familion} }
     jpa: { open-in-view: false, hibernate.ddl-auto: validate, properties.hibernate.jdbc.time_zone: UTC }
     flyway: { enabled: true, locations: classpath:db/migration }
     data.redis: { host: ${REDIS_HOST:localhost}, port: ${REDIS_PORT:6379} }
     mail: { host: ${MAIL_HOST:localhost}, port: ${MAIL_PORT:1025} }
     threads.virtual.enabled: true
     jackson: { default-property-inclusion: always, serialization.write-dates-as-timestamps: false }
   management:
     endpoints.web.exposure.include: health,info
     endpoint.health.probes.enabled: true
     health.mail.enabled: false
   springdoc: { api-docs.path: /v3/api-docs, swagger-ui.path: /swagger-ui.html }
   familion:
     app: { version: ${APP_VERSION:0.1.0}, public-url: ${APP_PUBLIC_URL:http://localhost:8088}, mail-from: ${MAIL_FROM:no-reply@familion.local} }
   ```
   Perfil `application-dev.yml` con logging `com.familion: DEBUG`.
4. **Jackson para dinero** `com.familion.common.web.JacksonConfig`: `BigDecimal` se serializa como **string plano** (`ToStringSerializer` + `WRITE_BIGDECIMAL_AS_PLAIN`); `LocalDate` como `yyyy-MM-dd`.
5. **Esqueleto de módulos** (un `package-info.java` con una línea de Javadoc describiendo el módulo): `com.familion.{auth,family,economics,finance,vehicles,analytics,notifications,receipts,common}`.
6. **Base de tests de integración** `src/test/java/com/familion/common/test/IntegrationTestBase.java`:
   ```java
   @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
   @Testcontainers
   public abstract class IntegrationTestBase {
     @Container @ServiceConnection
     static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
     @Container @ServiceConnection(name = "redis")
     static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
     @LocalServerPort protected int port;
     // helper: protected RestClient client() -> RestClient con baseUrl http://localhost:{port}
   }
   ```
   Contenedores `static` compartidos entre clases (reutilizables). Perfil de test `application-test.yml` si hace falta.
7. **Tests:**
   - `FlywayMigrationIT`: verifica que existen las 25 tablas: `families, family_currencies, users, family_invitations, refresh_tokens, email_security_codes, trusted_devices, economic_indices, accounts, categories, allowances, installment_plans, recurring_expenses, currency_exchanges, receipt_attachments, transactions, economic_snapshots, savings_goals, goal_contributions, vehicles, fuel_logs, maintenance_logs, notifications, push_subscriptions, app_releases`.
   - `ActuatorHealthIT`: `GET /actuator/health` → 200 `UP`.
   - `OpenApiIT`: `GET /v3/api-docs` → 200.
   - `JacksonConfigTest`: un record con `BigDecimal("36000.5000")` se serializa como `"36000.5000"`.
8. **Docker:** `backend/Dockerfile` multi-stage: `maven:3.9-eclipse-temurin-21` (cache de `~/.m2` con `--mount=type=cache`, `./mvnw -B -DskipTests package`) → `eclipse-temurin:21-jre-alpine`, usuario no-root `familion`, `EXPOSE 8081`, `HEALTHCHECK` con `wget -qO- http://localhost:8081/actuator/health/liveness`, `ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]`. Más `backend/.dockerignore` (`target/`, `.idea/`, etc.).
9. `backend/README.md` corto: requisitos, `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, cómo correr tests.

## Fuera de alcance
Seguridad/JWT, manejo global de errores, entidades JPA de negocio, docker-compose (T03), CI (T05).

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde (requiere Docker para Testcontainers).
- [ ] `V1__init_schema.sql` idéntico a `docs/database/schema.sql` (`diff` sin salida).
- [ ] Los 4 tests descritos existen y pasan.
- [ ] `docker build -t familion-backend ./backend` termina OK.
- [ ] Sin dependencias de Spring Security en el `pom.xml`.

## Verificación
```bash
cd backend && ./mvnw -B verify
diff src/main/resources/db/migration/V1__init_schema.sql ../docs/database/schema.sql && echo SAME
cd .. && docker build -t familion-backend ./backend
```

## Commits sugeridos
1. `chore(backend): scaffold spring boot core api with maven wrapper`
2. `feat(backend): add initial flyway schema migration`
3. `test(backend): add testcontainers integration test base and smoke tests`
4. `chore(backend): add multi-stage dockerfile`
